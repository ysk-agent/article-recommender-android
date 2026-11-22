package dev.moriya.discover.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single access point for the UI. Owns the active [BackendApi], the
 * [EventQueue], and the offline cache of the last feed / inbox
 * (design §10: feed and inbox are cached for offline use).
 *
 * The backend base URL lives in SharedPreferences ("backend_url").
 * Empty = fake backend. Setting a tailnet URL switches to [HttpBackend]
 * without any other code change.
 */
class Repository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val queue = EventQueue(appContext)

    var backend: BackendApi = buildBackend()
        private set

    private fun buildBackend(): BackendApi {
        val url = prefs.getString("backend_url", "")?.trim().orEmpty()
        return if (url.isEmpty()) FakeBackend() else HttpBackend(url)
    }

    fun backendUrl(): String = prefs.getString("backend_url", "")?.trim().orEmpty()

    fun setBackendUrl(url: String) {
        prefs.edit().putString("backend_url", url.trim()).apply()
        backend = buildBackend()
    }

    fun isFake(): Boolean = backend is FakeBackend

    // -- reads (with offline cache for feed / inbox) -------------------
    fun feed(): List<Article> = try {
        backend.feed().also { saveCache("feed", it) }
    } catch (t: Throwable) { loadCache("feed") }

    fun inbox(): List<Article> = try {
        backend.inbox().also { saveCache("inbox", it) }
    } catch (t: Throwable) { loadCache("inbox") }

    fun sources(): List<Source> = backend.sources()
    fun sourceArticles(id: String): List<Article> = backend.sourceArticles(id)
    fun history(): List<HistoryEntry> = backend.history()

    // -- user actions: record the event, flush, let UI refresh --------
    fun swipe(article: Article, action: SwipeAction): AppEvent {
        val type = when (action) {
            SwipeAction.DISLIKE -> EventType.DISLIKE
            SwipeAction.KNOWN -> EventType.KNOWN
            SwipeAction.INBOX -> EventType.INBOX
            SwipeAction.INBOX_HIGH -> EventType.INBOX_HIGH
        }
        val e = queue.enqueue(type, articleId = article.id, sourceId = article.sourceId)
        queue.flush(backend)
        return e
    }

    fun open(article: Article, dwellMs: Long? = null, completionRate: Double? = null): AppEvent {
        val e = queue.enqueue(EventType.OPEN, articleId = article.id, sourceId = article.sourceId,
            dwellMs = dwellMs, completionRate = completionRate)
        queue.flush(backend)
        return e
    }

    fun impression(article: Article) {
        // Impressions are aggregate-only: queued, never shown in History.
        queue.enqueue(EventType.IMPRESSION, articleId = article.id, sourceId = article.sourceId)
        queue.flush(backend)
    }

    fun undo(event: AppEvent) {
        // Fake backend: applied to its log directly. Real backend: no
        // undo path yet (HttpBackend gap 3) — the throw is swallowed and
        // the UI-side restore the caller already did stands.
        try { backend.undo(event) } catch (t: Throwable) { /* see gap 3 */ }
    }

    /** History correction (design §7): supersede the old event with
     *  the replacement action, so the backend can recompute the profile
     *  from the log without drift. */
    fun correct(entry: HistoryEntry, newAction: SwipeAction) {
        backend.correct(entry, newAction)
    }

    fun setSourceState(source: Source, state: SourceState) {
        backend.setSourceState(source.id, state)
    }

    /** Exploration share (design §5b): persisted locally for instant UI,
     *  and pushed to the backend, which owns feed composition. The
     *  change applies from the next feed build. */
    fun explorationShare(): Int {
        val local = prefs.getInt("exploration_share", -1)
        if (local >= 0) return local
        return try { backend.explorationShare() } catch (t: Throwable) { 20 }
            .also { prefs.edit().putInt("exploration_share", it).apply() }
    }

    fun setExplorationShare(percent: Int) {
        val v = percent.coerceIn(0, 50)
        prefs.edit().putInt("exploration_share", v).apply()
        try { backend.setExplorationShare(v) } catch (t: Throwable) { /* retried implicitly: local value is the UI truth until the backend read succeeds */ }
    }

    // -- offline cache (feed / inbox only, design §10) -----------------
    private fun saveCache(name: String, articles: List<Article>) {
        val arr = JSONArray()
        for (a in articles) arr.put(JSONObject().apply {
            put("id", a.id); put("sourceId", a.sourceId); put("sourceName", a.sourceName)
            put("title", a.title); put("url", a.url)
            a.thumbnailUrl?.let { put("thumb", it) }
            put("ts", a.publishedAtEpochMs); put("topic", a.topic)
            put("slot", a.slotType.name)
            a.clusterId?.let { put("cluster", it) }
        })
        File(appContext.filesDir, "cache-$name.json").writeText(arr.toString())
    }

    private fun loadCache(name: String): List<Article> {
        val f = File(appContext.filesDir, "cache-$name.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                Article(j.getString("id"), j.getString("sourceId"), j.getString("sourceName"),
                    j.getString("title"), j.getString("url"),
                    j.optString("thumb").ifEmpty { null }, j.getLong("ts"), j.getString("topic"),
                    SlotType.valueOf(j.optString("slot", "EXPLOIT")),
                    j.optString("cluster").ifEmpty { null })
            }
        } catch (t: Throwable) { emptyList() }
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
