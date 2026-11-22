package dev.moriya.discover.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real-backend client (design §10). JSON over HTTP against the base URL
 * from settings (a tailnet address in production). Kept deliberately
 * thin: parsing mirrors the fake backend's shapes one-to-one.
 * Milestone 1 implements reads + event posting; it is not yet exercised
 * against a live server because the backend does not exist yet.
 */
class HttpBackend(private val baseUrl: String) : BackendApi {
    private val base = baseUrl.trimEnd('/')

    override fun feed(): List<Article> = parseArticles(get("/feed"))
    override fun inbox(): List<Article> = parseArticles(get("/inbox"))
    override fun sourceArticles(sourceId: String): List<Article> = parseArticles(get("/sources/$sourceId"))

    override fun sources(): List<Source> {
        val arr = JSONArray(get("/sources"))
        return (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            Source(j.getString("id"), j.getString("name"), j.getString("domain"),
                SourceState.valueOf(j.optString("state", "NEUTRAL")),
                topics = j.optJSONArray("topics")?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: emptyList(),
                statsShown = j.optInt("statsShown"), statsOpened = j.optInt("statsOpened"),
                statsHighRated = j.optInt("statsHighRated"), statsRegretRate = j.optDouble("statsRegretRate", 0.0))
        }
    }

    override fun history(): List<HistoryEntry> {
        val arr = JSONArray(get("/history"))
        return (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            HistoryEntry(j.getString("eventId"), EventType.valueOf(j.getString("type")),
                j.optString("articleId").ifEmpty { null }, j.optString("articleTitle").ifEmpty { null },
                j.optString("sourceId").ifEmpty { null }, j.optString("sourceName").ifEmpty { null },
                j.getLong("ts"), j.optString("supersededBy").ifEmpty { null })
        }
    }

    override fun postEvents(events: List<AppEvent>) {
        val arr = JSONArray()
        for (e in events) arr.put(JSONObject().apply {
            put("id", e.id); put("type", e.type.name); put("ts", e.timestampEpochMs)
            e.articleId?.let { put("articleId", it) }
            e.sourceId?.let { put("sourceId", it) }
            e.supersedesEventId?.let { put("supersedes", it) }
            e.dwellMs?.let { put("dwellMs", it) }
            e.completionRate?.let { put("completion", it) }
        })
        post("/events", JSONObject().put("events", arr).toString())
    }

    override fun setSourceState(sourceId: String, state: SourceState) {
        val path = when (state) {
            SourceState.FOLLOWING -> "/sources/$sourceId/follow"
            SourceState.BLOCKED -> "/sources/$sourceId/block"
            SourceState.NEUTRAL -> "/sources/$sourceId/unblock"
        }
        post(path, "{}")
    }

    private fun parseArticles(body: String): List<Article> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            Article(j.getString("id"), j.getString("sourceId"), j.getString("sourceName"),
                j.getString("title"), j.getString("url"),
                j.optString("thumbnailUrl").ifEmpty { null }, j.getLong("publishedAt"),
                j.getString("topic"), SlotType.valueOf(j.optString("slot", "EXPLOIT")),
                j.optString("clusterId").ifEmpty { null })
        }
    }

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
