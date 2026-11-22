package dev.moriya.discover.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Real-backend client, aligned with the backend M1 API as built in
 * ~/workspace/discover-recommender/backend (FastAPI, snake_case fields,
 * integer IDs, responses wrapped in {"items": [...]}).
 *
 * Contract as of 2026-11-22:
 *   GET  /feed, /inbox            -> {"items": [article...]}
 *   GET  /sources                 -> {"items": [source...]}
 *   GET  /sources/{id}            -> source + {"articles": [...]}
 *   POST /sources/{id}/follow|block|unfollow
 *   POST /events                  -> {"events": [{type, article_id, source_id, payload}]}
 *   GET  /history                 -> {"items": [event...]} (active events only)
 *   POST /history/{id}/correct    -> {"new_type": "..."} (superseding)
 *   Article thumbnails come from the backend's thumbnail_url
 *   (og:image / feed media, decided by Yosuke 2026-11-22).
 *
 * Known gaps in the backend contract (flagged to the design chat,
 * 2026-11-22 — the app does not silently work around them):
 *   1. No unblock endpoint/event: follow/block/unfollow only, and
 *      unfollow is the only route back to neutral.
 *   2. No /settings endpoint: exploration_rate exists in the backend
 *      store but is not exposed over HTTP yet.
 *   3. No undo/cancel path: /history/{id}/correct only replaces an
 *      event with another explicit type, so an undo cannot be
 *      expressed. undo() below throws and Repository swallows it;
 *      the local UI state is restored regardless.
 *   4. Sources carry no topics or regret rate yet; stats are
 *      shown/opened/high_rated only.
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
                supersededBy = null, // backend history lists active events only
            )
        }
    }

    override fun postEvents(events: List<AppEvent>) {
        val arr = JSONArray()
        for (e in events) arr.put(JSONObject().apply {
            put("type", eventTypeToSnake(e.type))
            e.articleId?.toIntOrNull()?.let { put("article_id", it) }
            e.sourceId?.toIntOrNull()?.let { put("source_id", it) }
            val payload = JSONObject()
            e.dwellMs?.let { payload.put("dwell_ms", it) }
            e.completionRate?.let { payload.put("completion", it) }
            e.supersedesEventId?.let { payload.put("supersedes_event_id", it) }
            put("payload", payload)
        })
        post("/events", JSONObject().put("events", arr).toString())
    }

    override fun setSourceState(sourceId: String, state: SourceState) {
        val path = when (state) {
            SourceState.FOLLOWING -> "/sources/$sourceId/follow"
            SourceState.BLOCKED -> "/sources/$sourceId/block"
            // Gap 1: no unblock endpoint; unfollow is the only route to neutral.
            SourceState.NEUTRAL -> "/sources/$sourceId/unfollow"
        }
        post(path, "{}")
    }

    override fun correct(entry: HistoryEntry, newAction: SwipeAction) {
        post("/history/${entry.eventId}/correct",
            JSONObject().put("new_type", eventTypeToSnake(actionType(newAction))).toString())
    }

    override fun undo(event: AppEvent) {
        throw UnsupportedOperationException("backend has no undo endpoint yet (gap 3)")
    }

    override fun explorationShare(): Int =
        // Gap 2: no /settings endpoint yet; Repository falls back to local.
        JSONObject(get("/settings")).optInt("explorationShare", 20)

    override fun setExplorationShare(percent: Int) {
        post("/settings", JSONObject().put("explorationShare", percent.coerceIn(0, 50)).toString())
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
                topic = j.optString("topic"),
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
        val homepage = j.optString("homepage")
        val feedUrl = j.optString("feed_url")
        val domain = try {
            URI(if (homepage.isNotEmpty()) homepage else feedUrl).host ?: ""
        } catch (t: Throwable) { "" }
        return Source(
            id = j.getInt("id").toString(),
            name = j.getString("name"),
            domain = domain,
            state = when (j.optString("follow_state")) {
                "following" -> SourceState.FOLLOWING
                "blocked" -> SourceState.BLOCKED
                else -> SourceState.NEUTRAL
            },
            topics = emptyList(), // gap 4
            statsShown = stats?.optInt("shown") ?: 0,
            statsOpened = stats?.optInt("opened") ?: 0,
            statsHighRated = stats?.optInt("high_rated") ?: 0,
            statsRegretRate = 0.0, // gap 4
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
    private fun post(path: String, body: String) { request("POST", path, body) }

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
