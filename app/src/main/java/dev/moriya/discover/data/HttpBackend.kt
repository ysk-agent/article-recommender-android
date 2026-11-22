package dev.moriya.discover.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Real-backend client, implementing the final contract
 * (~/workspace/discover-recommender/CONTRACT.md, 2026-11-22):
 * snake_case fields, integer IDs, {"items": [...]} wrappers,
 * client_event_id idempotency, /settings with exploration_rate as a
 * fraction, distinct unblock, /history/{id}/undo and /correct,
 * history including annulled entries with superseded_by, and source
 * domain/topics/regret_rate supplied by the backend.
 */
class HttpBackend(private val baseUrl: String) : BackendApi {
    private val base = baseUrl.trimEnd('/')

    override fun feed(): List<Article> = parseArticles(get("/feed"))
    override fun inbox(): List<Article> = parseArticles(get("/inbox"))

    override fun sourceArticles(sourceId: String): List<Article> {
        val j = JSONObject(get("/sources/$sourceId"))
        return parseArticleArray(j.optJSONArray("articles") ?: JSONArray())
    }

    override fun sources(): List<Source> {
        val items = JSONObject(get("/sources")).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i -> parseSource(items.getJSONObject(i)) }
    }

    override fun history(): List<HistoryEntry> {
        val items = JSONObject(get("/history")).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val j = items.getJSONObject(i)
            val article = j.optJSONObject("article")
            HistoryEntry(
                eventId = j.getInt("event_id").toString(),
                type = eventTypeFromSnake(j.getString("type")),
                articleId = if (j.isNull("article_id")) null else j.getInt("article_id").toString(),
                articleTitle = article?.optString("title")?.ifEmpty { null },
                sourceId = if (j.isNull("source_id")) null else j.getInt("source_id").toString(),
                sourceName = article?.optString("source_name")?.ifEmpty { null },
                timestampEpochMs = parseIso(j.optString("created_at")),
                supersededBy = if (j.isNull("superseded_by")) null
                               else j.getInt("superseded_by").toString(),
            )
        }
    }

    override fun postEvents(events: List<AppEvent>): List<String> {
        val arr = JSONArray()
        for (e in events) arr.put(JSONObject().apply {
            put("type", eventTypeToSnake(e.type))
            put("client_event_id", e.id)
            e.articleId?.toIntOrNull()?.let { put("article_id", it) }
            e.sourceId?.toIntOrNull()?.let { put("source_id", it) }
            val payload = JSONObject()
            e.dwellMs?.let { payload.put("dwell_ms", it) }
            e.completionRate?.let { payload.put("completion", it) }
            e.newType?.let { payload.put("new_type", eventTypeToSnake(it)) }
            e.supersedesEventId?.toIntOrNull()?.let {
                payload.put("supersedes_event_id", it)
                put("supersedes_event_id", it)
            }
            put("payload", payload)
        })
        val response = post("/events", JSONObject().put("events", arr).toString())
        val ids = JSONObject(response).optJSONArray("event_ids") ?: JSONArray()
        return (0 until ids.length()).map { ids.getInt(it).toString() }
    }

    override fun setSourceState(source: Source, state: SourceState) {
        val sourceId = source.id
        val path = when (state) {
            SourceState.FOLLOWING -> "/sources/$sourceId/follow"
            SourceState.BLOCKED -> "/sources/$sourceId/block"
            // Contract: blocked -> unblock, following -> unfollow.
            SourceState.NEUTRAL ->
                if (source.state == SourceState.BLOCKED) "/sources/$sourceId/unblock"
                else "/sources/$sourceId/unfollow"
        }
        if (state == SourceState.NEUTRAL && source.state == SourceState.NEUTRAL) return
        post(path, "{}")
    }

    override fun correct(entry: HistoryEntry, newAction: SwipeAction) {
        post("/history/${entry.eventId}/correct",
            JSONObject().put("new_type", eventTypeToSnake(actionType(newAction))).toString())
    }

    override fun undo(event: AppEvent) {
        // Repository translates the client event id to the server id
        // (captured from the /events response) before calling this.
        request("POST", "/history/${event.id}/undo", null)
    }

    override fun explorationShare(): Int {
        val rate = JSONObject(get("/settings")).optDouble("exploration_rate", 0.20)
        return (rate * 100).toInt().coerceIn(0, 50)
    }

    override fun setExplorationShare(percent: Int) {
        val fraction = percent.coerceIn(0, 50) / 100.0
        post("/settings", JSONObject().put("exploration_rate", fraction).toString())
    }

    // -- parsing ---------------------------------------------------------
    private fun parseArticles(body: String): List<Article> {
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return parseArticleArray(items)
    }

    private fun parseArticleArray(arr: JSONArray): List<Article> =
        (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            Article(
                id = j.getInt("id").toString(),
                sourceId = j.getInt("source_id").toString(),
                sourceName = j.optString("source_name"),
                title = j.getString("title"),
                url = j.getString("canonical_url"),
                thumbnailUrl = if (j.isNull("thumbnail_url")) null else j.optString("thumbnail_url").ifEmpty { null },
                publishedAtEpochMs = parseIso(j.optString("published_at")),
                topic = if (j.isNull("topic")) "" else j.optString("topic"),
                slotType = when (j.optString("slot_type")) {
                    "near_explore" -> SlotType.EXPLORE_NEAR
                    "far_explore" -> SlotType.EXPLORE_FAR
                    else -> SlotType.EXPLOIT
                },
                clusterId = if (j.isNull("event_cluster_id")) null else j.getInt("event_cluster_id").toString(),
            )
        }

    private fun parseSource(j: JSONObject): Source {
        val stats = j.optJSONObject("stats")
        val topicsArr = j.optJSONArray("topics")
        return Source(
            id = j.getInt("id").toString(),
            name = j.getString("name"),
            domain = if (j.isNull("domain")) "" else j.optString("domain"),
            state = when (j.optString("follow_state")) {
                "following" -> SourceState.FOLLOWING
                "blocked" -> SourceState.BLOCKED
                else -> SourceState.NEUTRAL
            },
            topics = if (topicsArr == null) emptyList()
                     else (0 until topicsArr.length()).map { topicsArr.getString(it) },
            statsShown = stats?.optInt("shown") ?: 0,
            statsOpened = stats?.optInt("opened") ?: 0,
            statsHighRated = stats?.optInt("high_rated") ?: 0,
            statsRegretRate = stats?.optDouble("regret_rate", 0.0) ?: 0.0,
        )
    }

    private fun parseIso(value: String): Long {
        if (value.isEmpty()) return 0
        return try { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        catch (t: Throwable) {
            try { Instant.parse(value).toEpochMilli() } catch (t2: Throwable) { 0 }
        }
    }

    companion object {
        fun actionType(action: SwipeAction): EventType = when (action) {
            SwipeAction.DISLIKE -> EventType.DISLIKE
            SwipeAction.KNOWN -> EventType.KNOWN
            SwipeAction.INBOX -> EventType.INBOX
            SwipeAction.INBOX_HIGH -> EventType.INBOX_HIGH
        }

        fun eventTypeToSnake(type: EventType): String = when (type) {
            EventType.INBOX_HIGH -> "inbox_high"
            else -> type.name.lowercase()
        }

        fun eventTypeFromSnake(name: String): EventType = when (name) {
            "inbox_high" -> EventType.INBOX_HIGH
            "unblock" -> EventType.UNBLOCK
            else -> try { EventType.valueOf(name.uppercase()) } catch (t: Throwable) { EventType.OPEN }
        }
    }

    // -- HTTP ------------------------------------------------------------
    private fun get(path: String): String = request("GET", path, null)
    private fun post(path: String, body: String): String = request("POST", path, body)

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000; conn.readTimeout = 8000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        check(code in 200..299) { "HTTP $code for $path" }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
