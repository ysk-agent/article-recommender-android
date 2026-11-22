package dev.moriya.discover.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import dev.moriya.discover.data.Article
import dev.moriya.discover.data.Repository
import dev.moriya.discover.data.SourceState

/** Long-press source sheet (design §3, §6): source name, Follow / Block,
 *  and a route to the source page — without losing feed scroll position.
 *  Block is strong, so it asks for confirmation first (§6). */
class SourceSheet : DialogFragment() {

    override fun onCreateDialog(saved: Bundle?): Dialog {
        val repo = Repository.get(requireContext())
        val sourceId = requireArguments().getString("sourceId")!!
        val sourceName = requireArguments().getString("sourceName")!!
        val ctx = requireContext()

        val nameView = ctx.textView(sourceName, 17f, bold = true, color = ctx.colText())
        val stateView = ctx.textView(sizeSp = 13f, color = ctx.colTextSub()).apply { setPadding(0, ctx.dp(4), 0, 0) }
        val followBtn = Button(ctx).apply { stylePrimary(ctx); layoutParams = matchWrap(ctx, top = 12) }
        val blockBtn = Button(ctx).apply { styleOutline(ctx); layoutParams = matchWrap(ctx, top = 6) }
        val openBtn = Button(ctx).apply { styleOutline(ctx);
            text = "媒体ページを開く"; layoutParams = matchWrap(ctx, top = 6)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(20), ctx.dp(20), ctx.dp(20))
            addView(nameView); addView(stateView); addView(followBtn); addView(blockBtn); addView(openBtn)
        }

        fun current() = repo.sources().firstOrNull { it.id == sourceId }
        fun renderState() {
            val s = current()
            stateView.text = when (s?.state) {
                SourceState.FOLLOWING -> "状態: フォロー中（推薦で優遇されます）"
                SourceState.BLOCKED -> "状態: ブロック中"
                else -> "状態: 通常"
            }
            followBtn.text = if (s?.state == SourceState.FOLLOWING) "フォローを外す" else "フォロー"
            blockBtn.text = if (s?.state == SourceState.BLOCKED) "ブロックを解除" else "ブロック"
        }
        renderState()

        followBtn.setOnClickListener {
            val s = current() ?: return@setOnClickListener
            repo.setSourceState(s, if (s.state == SourceState.FOLLOWING) SourceState.NEUTRAL else SourceState.FOLLOWING)
            renderState()
        }
        blockBtn.setOnClickListener {
            val s = current() ?: return@setOnClickListener
            if (s.state == SourceState.BLOCKED) {
                repo.setSourceState(s, SourceState.NEUTRAL); renderState()
            } else {
                AlertDialog.Builder(ctx)
                    .setTitle("${s.name} をブロックしますか？")
                    .setMessage("ブロックした媒体はフィードに一切表示されなくなります。解除は「媒体」タブから行えます。")
                    .setPositiveButton("ブロック") { _, _ ->
                        repo.setSourceState(s, SourceState.BLOCKED)
                        (parentFragment as? ArticleListFragment)?.refresh()
                        dismiss()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
        openBtn.setOnClickListener {
            dismiss()
            startActivity(SourceActivity.intent(ctx, sourceId))
        }
        return AlertDialog.Builder(ctx).setView(root).create()
    }

    private fun matchWrap(ctx: android.content.Context, top: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(top) }

    companion object {
        fun show(host: Fragment, article: Article) {
            SourceSheet().apply {
                arguments = Bundle().apply {
                    putString("sourceId", article.sourceId)
                    putString("sourceName", article.sourceName)
                }
            }.show(host.childFragmentManager, "sourceSheet")
        }
    }
}
