package dev.moriya.discover.data

/** Feed slot attribution (design §10): outcomes are attributed to
 *  exploit vs explore later, so the slot type travels with the article. */
enum class SlotType { EXPLOIT, EXPLORE_NEAR, EXPLORE_FAR }

enum class SourceState { NEUTRAL, FOLLOWING, BLOCKED }

/** Explicit two-stage swipe actions (design §3). */
enum class SwipeAction {
    DISLIKE,      // left stage 1: explicit negative
    KNOWN,        // left stage 2: already known / read elsewhere, no topic penalty
    INBOX,        // right stage 1: save for later
    INBOX_HIGH,   // right stage 2: inbox + high rating
}

data class Source(
    val id: String,
    val name: String,
    val domain: String,
    var state: SourceState = SourceState.NEUTRAL,
    val topics: List<String> = emptyList(),
    // Yosuke's own per-source statistics (design §6), computed backend-side
    // in the real system; the fake backend supplies plausible values.
    val statsShown: Int = 0,
    val statsOpened: Int = 0,
    val statsHighRated: Int = 0,
    val statsRegretRate: Double = 0.0,
)

data class Article(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val publishedAtEpochMs: Long,
    val topic: String,
    val slotType: SlotType = SlotType.EXPLOIT,
    val clusterId: String? = null,
)

/** One entry of the History tab (design §7): explicit operations and
 *  opened articles only. Impressions never appear here. */
data class HistoryEntry(
    val eventId: String,
    val type: EventType,
    val articleId: String?,
    val articleTitle: String?,
    val sourceId: String?,
    val sourceName: String?,
    val timestampEpochMs: Long,
    /** Set when this entry was superseded by a correction event. */
    val supersededBy: String? = null,
    val note: String? = null,
)

enum class EventType {
    IMPRESSION,   // aggregate only, never listed in History
    SEEN,         // row >=50% visible for a sustained dwell (weak, article-only)
    OPEN,
    DISLIKE, KNOWN, INBOX, INBOX_HIGH,
    FOLLOW, BLOCK, UNBLOCK, UNFOLLOW,
    CORRECTION,   // supersedes an earlier event (design §7)
    UNDO,         // supersedes the event it cancels
}

/**
 * Append-only event, queued on-device and batch-synced (design §10).
 * Dwell / completion are measured and carried here, but are NOT model
 * signals (design §5, tentative decision) — [usedAsModelSignal] stays
 * false for them so the backend cannot silently start using them.
 */
data class AppEvent(
    val id: String,
    val type: EventType,
    val timestampEpochMs: Long,
    val articleId: String? = null,
    val sourceId: String? = null,
    val supersedesEventId: String? = null,
    val dwellMs: Long? = null,
    val completionRate: Double? = null,
    val usedAsModelSignal: Boolean = false,
)
