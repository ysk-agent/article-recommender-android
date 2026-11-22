package dev.moriya.discover.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * On-device event queue (design §10): events are appended to a JSONL
 * file first (so nothing is lost if the app dies), then flushed to the
 * backend in batches. A flush that the backend accepts clears the file;
 * against the fake backend the flush is immediate and in-process.
 */
class EventQueue(context: Context) {
    private val file = File(context.filesDir, "event-queue.jsonl")
    private val pending = mutableListOf<AppEvent>()

    init {
        if (file.exists()) {
            file.forEachLine { line ->
                if (line.isNotBlank()) decode(line)?.let { pending.add(it) }
            }
        }
    }

    fun enqueue(type: EventType, articleId: String? = null, sourceId: String? = null,
                supersedesEventId: String? = null, dwellMs: Long? = null,
                completionRate: Double? = null): AppEvent {
        val e = AppEvent(UUID.randomUUID().toString(), type, System.currentTimeMillis(),
            articleId, sourceId, supersedesEventId, dwellMs, completionRate)
        pending.add(e)
        file.appendText(encode(e) + "\n")
        return e
    }

    fun pendingCount(): Int = pending.size

    /** Returns the flushed batch, or empty if the backend rejected it. */
    fun flush(backend: BackendApi): List<AppEvent> {
        if (pending.isEmpty()) return emptyList()
        val batch = pending.toList()
        backend.postEvents(batch)
        pending.clear()
        file.writeText("")
        return batch
    }

    private fun encode(e: AppEvent): String = JSONObject().apply {
        put("id", e.id); put("type", e.type.name); put("ts", e.timestampEpochMs)
        e.articleId?.let { put("articleId", it) }
        e.sourceId?.let { put("sourceId", it) }
        e.supersedesEventId?.let { put("supersedes", it) }
        e.dwellMs?.let { put("dwellMs", it) }
        e.completionRate?.let { put("completion", it) }
    }.toString()

    private fun decode(line: String): AppEvent? = try {
        val j = JSONObject(line)
        AppEvent(
            id = j.getString("id"),
            type = EventType.valueOf(j.getString("type")),
            timestampEpochMs = j.getLong("ts"),
            articleId = j.optString("articleId").ifEmpty { null },
            sourceId = j.optString("sourceId").ifEmpty { null },
            supersedesEventId = j.optString("supersedes").ifEmpty { null },
            dwellMs = if (j.has("dwellMs")) j.getLong("dwellMs") else null,
            completionRate = if (j.has("completion")) j.getDouble("completion") else null,
        )
    } catch (t: Throwable) { null }
}
