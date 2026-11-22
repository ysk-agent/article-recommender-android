package dev.moriya.discover.data

/**
 * The API boundary between the Android app and the Python backend
 * (design §10). The app is developed against this interface with
 * [FakeBackend]; the real backend is reached through [HttpBackend]
 * by setting a base URL in the app settings. Nothing outside the
 * data layer knows which implementation is active.
 *
 * HTTP mapping (real backend):
 *   GET  /feed            -> feed()
 *   GET  /inbox           -> inbox()
 *   GET  /sources         -> sources()
 *   GET  /sources/{id}    -> sourceArticles()
 *   GET  /history         -> history()
 *   POST /events          -> postEvents()   (batch, append-only)
 *   POST /sources/{id}/follow|block|unblock|unfollow
 *                         -> setSourceState() (recorded as events)
 *   History correction is a POST /events entry of type CORRECTION
 *   whose supersedesEventId names the event it replaces (§7).
 */
interface BackendApi {
    fun feed(): List<Article>
    fun inbox(): List<Article>
    fun sources(): List<Source>
    fun sourceArticles(sourceId: String): List<Article>
    fun history(): List<HistoryEntry>
    /** Posts a batch; returns the backend's event ids in batch order
     *  (the fake returns the client ids, which are its own ids). */
    fun postEvents(events: List<AppEvent>): List<String>
    /** [source] carries the current state: reaching NEUTRAL from
     *  BLOCKED is unblock, from FOLLOWING is unfollow (contract). */
    fun setSourceState(source: Source, state: SourceState)
    /** History correction (design §7): supersede the entry's event with
     *  the replacement action. The real backend exposes this as
     *  POST /history/{id}/correct; the fake applies it to its log. */
    fun correct(entry: HistoryEntry, newAction: SwipeAction)
    /** Undo a just-applied event. The real backend has no undo path
     *  yet (see HttpBackend gap 3) and throws; callers must tolerate it. */
    fun undo(event: AppEvent)
    /** Exploration share, percent 0–50 (design §5b). A user setting,
     *  owned by the backend's feed composition; the app reads/writes it. */
    fun explorationShare(): Int
    fun setExplorationShare(percent: Int)
}
