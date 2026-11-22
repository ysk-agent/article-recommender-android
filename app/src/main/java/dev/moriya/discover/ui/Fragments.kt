package dev.moriya.discover.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.moriya.discover.data.Article
import dev.moriya.discover.data.EventType
import dev.moriya.discover.data.HistoryEntry
import dev.moriya.discover.data.Repository
import dev.moriya.discover.data.Source
import dev.moriya.discover.data.SourceState
import dev.moriya.discover.data.SwipeAction

/** A RecyclerView with a centred empty-state label, built in code. */
class ListScaffold(context: android.content.Context) {
    val root = FrameLayout(context)
    val recycler = RecyclerView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val emptyText: TextView = context.textView(sizeSp = 15f).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        visibility = View.GONE
    }
    val footer: TextView = context.textView(sizeSp = 12f).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
        visibility = View.GONE
    }

    init {
        root.setBackgroundColor(context.colBg())
        emptyText.setTextColor(context.colTextSub())
        footer.setTextColor(context.colTextSub())
        root.addView(recycler); root.addView(emptyText); root.addView(footer)
    }

    fun showEmpty(message: String?) {
        if (message == null) { emptyText.visibility = View.GONE }
        else { emptyText.text = message; emptyText.visibility = View.VISIBLE }
    }
}

/** Behaviour shared by every article list (Feed / Inbox / Source page):
 *  tap opens, source-name tap goes to the source page, long-press opens
 *  the source sheet, two-stage swipe applies an action with undo. */
abstract class ArticleListFragment : Fragment() {
    protected lateinit var scaffold: ListScaffold
    protected lateinit var adapter: ArticleAdapter
    protected val repo get() = Repository.get(requireContext())
    private val impressed = mutableSetOf<String>()

    abstract fun loadArticles(): List<Article>
    abstract fun emptyMessage(): String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        scaffold = ListScaffold(requireContext())
        return scaffold.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = ArticleAdapter(
            onTap = { openArticle(it) },
            onLongPress = { SourceSheet.show(this, it) },
            onSourceTap = { startActivity(SourceActivity.intent(requireContext(), it.sourceId)) },
            onBound = { a -> if (impressed.add(a.id)) repo.impression(a) },
        )
        scaffold.recycler.layoutManager = LinearLayoutManager(requireContext())
        scaffold.recycler.adapter = adapter
        val swipe = TwoStageSwipe { holder, action ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < adapter.items.size) {
                applySwipe(adapter.items[pos], action)
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(scaffold.recycler)
        refresh()
    }

    override fun onResume() { super.onResume(); if (::adapter.isInitialized) refresh() }

    fun refresh() {
        val list = loadArticles()
        adapter.submit(list)
        scaffold.showEmpty(if (list.isEmpty()) emptyMessage() else null)
    }

    private fun applySwipe(article: Article, action: SwipeAction) {
        val index = adapter.remove(article)
        val event = repo.swipe(article, action)
        UndoBar.show(scaffold.root, "「${TwoStageSwipe.label(action)}」を適用しました") {
            repo.undo(event)
            if (index >= 0) adapter.insert(article, index)
            scaffold.showEmpty(null)
        }
        if (adapter.items.isEmpty()) scaffold.showEmpty(emptyMessage())
    }

    protected fun openArticle(article: Article) {
        // Articles open in the in-app WebView reader (Yosuke, 2026-11-22).
        // The OPEN event — with dwell and completion attached — is
        // recorded by the reader when it closes (design §5, §7).
        ReaderActivity.open(requireContext(), article)
    }
}

class FeedFragment : ArticleListFragment() {
    override fun loadArticles() = repo.feed()
    override fun emptyMessage() = "フィードは空です"
}

class InboxFragment : ArticleListFragment() {
    override fun loadArticles() = repo.inbox()
    override fun emptyMessage() = "Inboxは空です。右スワイプで記事を保存できます"
}

/** Simple three-line text row used by the Sources and History lists. */
class TextRowAdapter(
    private val rows: List<Triple<String, String, String>>,
    private val onTap: (Int) -> Unit,
) : RecyclerView.Adapter<TextRowAdapter.VH>() {
    class VH(val root: LinearLayout, val line1: TextView, val line2: TextView, val line3: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val line1 = ctx.textView(sizeSp = 14f, bold = true, color = ctx.colText())
        val line2 = ctx.textView(sizeSp = 13f, maxLines = 2, color = ctx.colTextSub()).apply { setPadding(0, ctx.dp(2), 0, 0) }
        val line3 = ctx.textView(sizeSp = 12f, color = ctx.colTextSub()).apply { setPadding(0, ctx.dp(2), 0, 0) }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(ctx.dp(12), ctx.dp(5), ctx.dp(12), ctx.dp(5)) }
            background = ctx.roundedBg(ctx.colSurface(), 16, ctx.colBorder())
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
            addView(line1); addView(line2); addView(line3)
        }
        return VH(root, line1, line2, line3)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (a, b, c) = rows[position]
        holder.line1.text = a; holder.line2.text = b; holder.line3.text = c
        holder.root.setOnClickListener { onTap(position) }
    }
}

/** Sources tab (design §6): followed / blocked lists live here; this is
 *  the unblock path, since blocked sources never appear in the feed.
 *  M1 also hosts the backend-URL setting here (footer, long-press). */
class SourcesFragment : Fragment() {
    private lateinit var scaffold: ListScaffold
    private val repo get() = Repository.get(requireContext())
    private var sources: List<Source> = emptyList()
    private val rows = mutableListOf<Triple<String, String, String>>()
    private lateinit var listAdapter: TextRowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        scaffold = ListScaffold(requireContext())
        return scaffold.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        listAdapter = TextRowAdapter(rows) { pos ->
            startActivity(SourceActivity.intent(requireContext(), sources[pos].id))
        }
        scaffold.recycler.layoutManager = LinearLayoutManager(requireContext())
        scaffold.recycler.adapter = listAdapter
        scaffold.footer.setOnClickListener { startActivity(SettingsActivity.intent(requireContext())) }
        refresh()
    }

    override fun onResume() { super.onResume(); if (::listAdapter.isInitialized) refresh() }

    private fun refresh() {
        sources = repo.sources()
        rows.clear()
        for (s in sources) rows.add(Triple(
            when (s.state) {
                SourceState.FOLLOWING -> "フォロー中 ・ ${s.name}"
                SourceState.BLOCKED -> "ブロック中 ・ ${s.name}"
                SourceState.NEUTRAL -> s.name
            },
            s.domain,
            "トピック: ${s.topics.joinToString("、")}",
        ))
        listAdapter.notifyDataSetChanged()
        val mode = if (repo.isFake()) "モックデータ" else repo.backendUrl()
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        scaffold.footer.text = "Discover ${info.versionName} ・ 接続先: $mode（タップで設定）"
        scaffold.footer.visibility = View.VISIBLE
    }

}

/** History tab (design §7): a correction screen, not just a log.
 *  Only explicit operations and opened articles are listed. Tapping a
 *  swipe entry offers corrections (e.g. dislike -> already known),
 *  applied as superseding events, never as edits. */
class HistoryFragment : Fragment() {
    private lateinit var scaffold: ListScaffold
    private val repo get() = Repository.get(requireContext())
    private var entries: List<HistoryEntry> = emptyList()
    private val rows = mutableListOf<Triple<String, String, String>>()
    private lateinit var listAdapter: TextRowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        scaffold = ListScaffold(requireContext())
        return scaffold.root
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        listAdapter = TextRowAdapter(rows) { pos -> showCorrection(entries[pos]) }
        scaffold.recycler.layoutManager = LinearLayoutManager(requireContext())
        scaffold.recycler.adapter = listAdapter
        refresh()
    }

    override fun onResume() { super.onResume(); if (::listAdapter.isInitialized) refresh() }

    private fun refresh() {
        entries = repo.history()
        rows.clear()
        for (e in entries) rows.add(Triple(
            label(e.type) + if (e.supersededBy != null) "（取り消し済み）" else "",
            e.articleTitle ?: e.sourceName ?: "",
            listOfNotNull(
                e.sourceName, TimeFmt.dateTime(e.timestampEpochMs),
                if (e.type in SWIPE_TYPES && e.supersededBy == null) "タップで訂正" else null,
            ).joinToString(" ・ "),
        ))
        listAdapter.notifyDataSetChanged()
        scaffold.showEmpty(if (entries.isEmpty()) "まだ操作履歴がありません" else null)
    }

    private fun showCorrection(entry: HistoryEntry) {
        if (entry.type !in SWIPE_TYPES || entry.supersededBy != null) {
            UndoBar.toast(scaffold.root, "この項目は訂正対象ではありません")
            return
        }
        val actions = SwipeAction.values()
        val options = actions.map { TwoStageSwipe.label(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("操作を訂正する")
            .setItems(options) { _, which ->
                repo.correct(entry, actions[which])
                UndoBar.toast(scaffold.root, "「${options[which]}」に訂正しました")
                refresh()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    companion object {
        val SWIPE_TYPES = setOf(EventType.DISLIKE, EventType.KNOWN, EventType.INBOX, EventType.INBOX_HIGH)

        fun label(type: EventType): String = when (type) {
            EventType.DISLIKE -> "低評価"
            EventType.KNOWN -> "もう知っている"
            EventType.INBOX -> "Inboxへ保存"
            EventType.INBOX_HIGH -> "Inbox＋高評価"
            EventType.OPEN -> "開いた"
            EventType.FOLLOW -> "フォロー"
            EventType.BLOCK -> "ブロック"
            EventType.UNBLOCK -> "ブロック解除"
            EventType.UNFOLLOW -> "フォロー解除"
            EventType.CORRECTION -> "訂正"
            EventType.UNDO -> "取り消し"
            else -> type.name
        }
    }
}
