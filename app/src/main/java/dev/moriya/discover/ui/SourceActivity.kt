package dev.moriya.discover.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.moriya.discover.data.Repository
import dev.moriya.discover.data.SourceState

/** Source page (design §6): header with Follow / Block and state, the
 *  source's articles chronologically with the same row + swipe as the
 *  feed, Yosuke's statistics for the source, and its topics.
 *  Follow is a ranking boost only — the page itself is the guaranteed
 *  chronological route, and the header copy says so. */
class SourceActivity : AppCompatActivity() {
    private val repo get() = Repository.get(this)
    private lateinit var sourceId: String
    private lateinit var adapter: ArticleAdapter
    private lateinit var rootFrame: FrameLayout
    private lateinit var nameView: android.widget.TextView
    private lateinit var domainView: android.widget.TextView
    private lateinit var stateView: android.widget.TextView
    private lateinit var statsView: android.widget.TextView
    private lateinit var topicsView: android.widget.TextView
    private lateinit var followBtn: Button
    private lateinit var blockBtn: Button

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        // targetSdk 35 forces edge-to-edge on Android 15: without this,
        // content slides under the status bar / action bar and the tab
        // bar under the navigation bar (seen in the first CI run's
        // screenshots). Classic fitted layout is what this app wants.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        sourceId = intent.getStringExtra("sourceId")!!

        nameView = textView(sizeSp = 20f, bold = true)
        domainView = textView(sizeSp = 13f).apply { setPadding(0, dp(2), 0, 0) }
        stateView = textView(sizeSp = 13f).apply { setPadding(0, dp(6), 0, 0) }
        followBtn = Button(this).apply { isAllCaps = false }
        blockBtn = Button(this).apply { isAllCaps = false }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            addView(followBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(blockBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(8) })
        }
        statsView = textView(sizeSp = 13f).apply { setPadding(0, dp(12), 0, 0) }
        topicsView = textView(sizeSp = 13f).apply { setPadding(0, dp(4), 0, 0) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(nameView); addView(domainView); addView(stateView)
            addView(buttons); addView(statsView); addView(topicsView)
        }
        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header); addView(recycler)
        }
        rootFrame = FrameLayout(this).apply { addView(column) }
        setContentView(rootFrame)

        adapter = ArticleAdapter(
            onTap = { a -> ReaderActivity.open(this, a) },
            onLongPress = { /* already on the source page */ },
            onSourceTap = { /* already on the source page */ },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        ItemTouchHelper(TwoStageSwipe { holder, action ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < adapter.items.size) {
                val article = adapter.items[pos]
                val index = adapter.remove(article)
                val event = repo.swipe(article, action)
                UndoBar.show(rootFrame, "「${TwoStageSwipe.label(action)}」を適用しました") {
                    repo.undo(event); if (index >= 0) adapter.insert(article, index)
                }
            }
        }).attachToRecyclerView(recycler)
        render()
    }

    override fun onResume() { super.onResume(); if (::adapter.isInitialized) render() }

    private fun render() {
        val source = repo.sources().firstOrNull { it.id == sourceId } ?: return finish()
        title = source.name
        nameView.text = source.name
        domainView.text = source.domain
        stateView.text = when (source.state) {
            SourceState.FOLLOWING -> "状態: フォロー中（推薦で優遇されますが、表示の保証はありません）"
            SourceState.BLOCKED -> "状態: ブロック中（フィードに表示されません）"
            SourceState.NEUTRAL -> "状態: 通常"
        }
        followBtn.text = if (source.state == SourceState.FOLLOWING) "フォローを外す" else "フォロー"
        blockBtn.text = if (source.state == SourceState.BLOCKED) "ブロックを解除" else "ブロック"
        statsView.text = "あなたの統計: 表示 ${source.statsShown} ・ 開いた ${source.statsOpened} ・ " +
            "高評価 ${source.statsHighRated} ・ 後悔率 ${(source.statsRegretRate * 100).toInt()}%"
        topicsView.text = "扱うトピック: ${source.topics.joinToString("、")}"
        followBtn.setOnClickListener {
            repo.setSourceState(source, if (source.state == SourceState.FOLLOWING) SourceState.NEUTRAL else SourceState.FOLLOWING)
            render()
        }
        blockBtn.setOnClickListener {
            if (source.state == SourceState.BLOCKED) {
                repo.setSourceState(source, SourceState.NEUTRAL); render()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("${source.name} をブロックしますか？")
                    .setMessage("ブロックした媒体はフィードに一切表示されなくなります。解除はこのページか「媒体」タブから行えます。")
                    .setPositiveButton("ブロック") { _, _ -> repo.setSourceState(source, SourceState.BLOCKED); render() }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
        adapter.submit(repo.sourceArticles(sourceId))
    }

    companion object {
        fun intent(context: Context, sourceId: String): Intent =
            Intent(context, SourceActivity::class.java).putExtra("sourceId", sourceId)
    }
}
