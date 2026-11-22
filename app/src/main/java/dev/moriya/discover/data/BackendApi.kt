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
    fun postEvents(events: List<AppEvent>)
    fun setSourceState(sourceId: String, state: SourceState)
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
