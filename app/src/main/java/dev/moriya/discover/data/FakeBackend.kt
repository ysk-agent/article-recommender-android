package dev.moriya.discover.data

import java.util.UUID

/**
* In-memory fake of the Python backend, implementing the final
* contract (~/workspace/discover-recommender/CONTRACT.md, 2026-11-22)
* so mock and real behaviour do not drift:
* - events are append-only; undo annuls its target, a correction
* retargets its target's effective type (the target stays in
* effect, superseded_by == null), meta events never list in history;
* - history returns all explicit + open events, including annulled
* ones with superseded_by set;
* - reaching NEUTRAL from BLOCKED is unblock, from FOLLOWING unfollow;
* - exploration share is a percent at this boundary (the wire format
* converts to the exploration_rate fraction in HttpBackend).
*/
class FakeBackend: BackendApi {

private val sources = mutableListOf(
Source("s-fg", "Fed Guy", "fedguy.com", topics = listOf("Fed", "repo", "流動性"),
statsShown = 142, statsOpened = 38, statsHighRated = 9, statsRegretRate = 0.05),
Source("s-netinterest", "Net Interest", "netinterest.co", topics = listOf("銀行", "フィンテック"),
statsShown = 96, statsOpened = 31, statsHighRated = 7, statsRegretRate = 0.03),
Source("s-hn", "Hacker News", "news.ycombinator.com", topics = listOf("AI", "開発", "スタートアップ"),
statsShown = 420, statsOpened = 88, statsHighRated = 11, statsRegretRate = 0.18),
Source("s-arxiv", "arXiv cs.LG", "arxiv.org", topics = listOf("機械学習", "論文"),
statsShown = 210, statsOpened = 26, statsHighRated = 6, statsRegretRate = 0.08),
Source("s-nikkei", "日本経済新聞", "nikkei.com", topics = listOf("市況", "日銀", "企業"),
statsShown = 380, statsOpened = 64, statsHighRated = 5, statsRegretRate = 0.22),
Source("s-tabelog-mag", "食べログマガジン", "tabelog.com", topics = listOf("グルメ", "エリア"),
statsShown = 74, statsOpened = 19, statsHighRated = 4, statsRegretRate = 0.06),
Source("s-osm-blog", "OpenStreetMap Blog", "blog.openstreetmap.org", topics = listOf("地図", "OSM"),
statsShown = 41, statsOpened = 12, statsHighRated = 3, statsRegretRate = 0.0),
Source("s-calc-risk", "Calculated Risk", "calculatedriskblog.com", topics = listOf("住宅", "マクロ"),
statsShown = 118, statsOpened = 29, statsHighRated = 6, statsRegretRate = 0.04),
Source("s-clickbait", "バズ速ニュース", "buzzsoku.example", topics = listOf("芸能", "炎上"),
statsShown = 260, statsOpened = 41, statsHighRated = 0, statsRegretRate = 0.61),
)

private val now = System.currentTimeMillis()
private fun hoursAgo(h: Double) = now - (h * 3600_000).toLong()

private val articles = mutableListOf(
Article("a01", "s-fg", "Fed Guy", "The Fed's Balance Sheet Is Shrinking in a New Way", "https://fedguy.com/example1", null, hoursAgo(1.2), "流動性", SlotType.EXPLOIT, "c-fed-1"),
Article("a02", "s-hn", "Hacker News", "Show HN: A local-first recommender that never phones home", "https://news.ycombinator.com/item?id=1", null, hoursAgo(2.0), "開発", SlotType.EXPLORE_NEAR),
Article("a03", "s-nikkei", "日本経済新聞", "日銀、来週の会合でフォワードガイダンス修正案を検討", "https://www.nikkei.com/example3", null, hoursAgo(2.5), "日銀", SlotType.EXPLOIT),
Article("a04", "s-arxiv", "arXiv cs.LG", "Prototype-Based Interest Modelling for Single-User Recommendation", "https://arxiv.org/abs/example4", null, hoursAgo(3.1), "機械学習", SlotType.EXPLOIT),
Article("a05", "s-netinterest", "Net Interest", "How a Regional Bank Quietly Rewired Its Deposit Franchise", "https://www.netinterest.co/example5", null, hoursAgo(4.0), "銀行", SlotType.EXPLOIT),
Article("a06", "s-clickbait", "バズ速ニュース", "有名俳優が深夜に語った一言にスタジオ騒然！？", "https://buzzsoku.example/6", null, hoursAgo(4.4), "芸能", SlotType.EXPLOIT),
Article("a07", "s-tabelog-mag", "食べログマガジン", "神保町のカレー名店マップ、老舗5軒を歩く", "https://tabelog.com/example7", null, hoursAgo(5.0), "グルメ", SlotType.EXPLORE_NEAR),
Article("a08", "s-calc-risk", "Calculated Risk", "Housing Starts Rose in October, Permits Fell", "https://www.calculatedriskblog.com/example8", null, hoursAgo(6.3), "住宅", SlotType.EXPLOIT),
Article("a09", "s-osm-blog", "OpenStreetMap Blog", "WeeklyOSM: Tile Rendering Notes from the Community", "https://blog.openstreetmap.org/example9", null, hoursAgo(7.0), "地図", SlotType.EXPLORE_NEAR),
Article("a10", "s-fg", "Fed Guy", "Repo Fails Are Telling a Different Story This Month", "https://fedguy.com/example10", null, hoursAgo(8.2), "repo", SlotType.EXPLOIT, "c-fed-1"),
Article("a11", "s-hn", "Hacker News", "SQLite Is All You Need (for a Personal Feed)", "https://news.ycombinator.com/item?id=11", null, hoursAgo(9.0), "開発", SlotType.EXPLOIT),
Article("a12", "s-nikkei", "日本経済新聞", "円相場、一時158円台　介入警戒感が再び強まる", "https://www.nikkei.com/example12", null, hoursAgo(10.5), "市況", SlotType.EXPLOIT),
Article("a13", "s-arxiv", "arXiv cs.LG", "Exploration Quotas in Bandit Systems with Sparse Explicit Feedback", "https://arxiv.org/abs/example13", null, hoursAgo(12.0), "論文", SlotType.EXPLORE_FAR),
Article("a14", "s-netinterest", "Net Interest", "The Plumbing Behind Instant Payments, Explained Slowly", "https://www.netinterest.co/example14", null, hoursAgo(14.0), "フィンテック", SlotType.EXPLOIT),
Article("a15", "s-tabelog-mag", "食べログマガジン", "地図から探すランチ：半径500mの幸せ", "https://tabelog.com/example15", null, hoursAgo(16.0), "グルメ", SlotType.EXPLOIT),
Article("a16", "s-calc-risk", "Calculated Risk", "Mortgage Rates and the Lock-In Effect, Updated Charts", "https://www.calculatedriskblog.com/example16", null, hoursAgo(19.0), "マクロ", SlotType.EXPLORE_NEAR),
Article("a17", "s-hn", "Hacker News", "Ask HN: How do you triage 300 articles a day?", "https://news.ycombinator.com/item?id=17", null, hoursAgo(22.0), "スタートアップ", SlotType.EXPLORE_FAR),
Article("a18", "s-clickbait", "バズ速ニュース", "あの人気番組が終了！？理由がヤバすぎた…", "https://buzzsoku.example/18", null, hoursAgo(25.0), "炎上", SlotType.EXPLOIT),
)

private val inboxIds = linkedSetOf<String>()
private val removedFromFeed = mutableSetOf<String>()
private val eventLog = mutableListOf<AppEvent>()
private val titlesById = articles.associate { it.id to it.title}
private val sourceNameById = sources.associate { it.id to it.name}

override fun feed(): List<Article> {
val blocked = sources.filter { it.state == SourceState.BLOCKED}.map { it.id}.toSet()
return articles.filter { it.id!in removedFromFeed && it.sourceId!in blocked}
.sortedByDescending { it.publishedAtEpochMs}
}

override fun inbox(): List<Article> =
articles.filter { it.id in inboxIds}.sortedByDescending { it.publishedAtEpochMs}

override fun sources(): List<Source> = sources.toList()

override fun sourceArticles(sourceId: String): List<Article> =
articles.filter { it.sourceId == sourceId}.sortedByDescending { it.publishedAtEpochMs}

override fun history(): List<HistoryEntry> {
val annulledBy = mutableMapOf<String, String>()
val retarget = mutableMapOf<String, EventType>()
for (e in eventLog) {
val target = e.supersedesEventId?: continue
when (e.type) {
EventType.UNDO -> annulledBy[target] = e.id
EventType.CORRECTION -> e.newType?.let { retarget[target] = it}
else -> {}
}
}
return eventLog
.filter { it.type!= EventType.IMPRESSION && it.type!= EventType.SEEN &&
it.type!= EventType.UNDO && it.type!= EventType.CORRECTION}
.sortedByDescending { it.timestampEpochMs}
.map { e ->
HistoryEntry(
eventId = e.id,
type = retarget[e.id]?: e.type,
articleId = e.articleId,
articleTitle = e.articleId?.let { titlesById[it]},
sourceId = e.sourceId,
sourceName = e.sourceId?.let { sourceNameById[it]},
timestampEpochMs = e.timestampEpochMs,
supersededBy = annulledBy[e.id],
)
}
}

override fun postEvents(events: List<AppEvent>): List<String> {
for (e in events) {
eventLog.add(e)
when (e.type) {
EventType.DISLIKE, EventType.KNOWN -> e.articleId?.let { removedFromFeed.add(it)}
EventType.INBOX, EventType.INBOX_HIGH -> e.articleId?.let { inboxIds.add(it)}
EventType.UNDO -> applyUndo(e)
EventType.CORRECTION -> applyCorrection(e)
else -> {}
}
}
return events.map { it.id}
}

private fun applyUndo(undo: AppEvent) {
val target = eventLog.firstOrNull { it.id == undo.supersedesEventId}?: return
when (target.type) {
EventType.DISLIKE, EventType.KNOWN -> target.articleId?.let { removedFromFeed.remove(it)}
EventType.INBOX, EventType.INBOX_HIGH -> target.articleId?.let { inboxIds.remove(it)}
else -> {}
}
}

private fun applyCorrection(correction: AppEvent) {
val target = eventLog.firstOrNull { it.id == correction.supersedesEventId}?: return
when (target.type) {
EventType.DISLIKE, EventType.KNOWN -> target.articleId?.let { removedFromFeed.remove(it)}
EventType.INBOX, EventType.INBOX_HIGH -> target.articleId?.let { inboxIds.remove(it)}
else -> {}
}
when (correction.newType) {
EventType.DISLIKE, EventType.KNOWN -> target.articleId?.let { removedFromFeed.add(it)}
EventType.INBOX, EventType.INBOX_HIGH -> target.articleId?.let { inboxIds.add(it)}
else -> {}
}
}

override fun correct(entry: HistoryEntry, newAction: SwipeAction) {
// Contract form 2: one correction event naming its target and
// new_type; the target stays in effect under the new type.
val type = when (newAction) {
SwipeAction.DISLIKE -> EventType.DISLIKE
SwipeAction.KNOWN -> EventType.KNOWN
SwipeAction.INBOX -> EventType.INBOX
SwipeAction.INBOX_HIGH -> EventType.INBOX_HIGH
}
postEvents(listOf(
AppEvent(UUID.randomUUID().toString(), EventType.CORRECTION,
System.currentTimeMillis(), entry.articleId, entry.sourceId,
supersedesEventId = entry.eventId, newType = type),
))
}

override fun undo(event: AppEvent) {
postEvents(listOf(
AppEvent(UUID.randomUUID().toString(), EventType.UNDO,
System.currentTimeMillis(), event.articleId, event.sourceId,
supersedesEventId = event.id),
))
}

private var exploration = 20
override fun explorationShare(): Int = exploration
override fun setExplorationShare(percent: Int) { exploration = percent.coerceIn(0, 50)}

override fun setSourceState(source: Source, state: SourceState) {
val idx = sources.indexOfFirst { it.id == source.id}
if (idx >= 0) sources[idx] = sources[idx].copy(state = state)
val type = when (state) {
SourceState.FOLLOWING -> EventType.FOLLOW
SourceState.BLOCKED -> EventType.BLOCK
// Vocabulary preserves how neutral was reached (contract).
SourceState.NEUTRAL ->
if (source.state == SourceState.BLOCKED) EventType.UNBLOCK else EventType.UNFOLLOW
}
eventLog.add(AppEvent(UUID.randomUUID().toString(), type, System.currentTimeMillis(), sourceId = source.id))
}
}
