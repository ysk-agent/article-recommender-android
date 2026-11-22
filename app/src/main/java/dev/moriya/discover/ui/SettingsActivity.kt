package dev.moriya.discover.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import dev.moriya.discover.data.Repository

/**
 * Settings screen (new screen, decided by Yosuke 2026-11-22).
 *
 * - Exploration share (design §5b): a user setting, default 20%,
 *   range 0–50% in 5% steps, 0% allowed. Applies from the next feed
 *   build. The near/far split inside the share stays internal and is
 *   not exposed here.
 * - Backend connection: base URL of the Python backend (tailnet
 *   address). Empty = in-process fake backend with mock data.
 * - App version, visible so the installed build can be checked at a
 *   glance against a handover note.
 * - A note that dwell / completion are logged but not used for ranking.
 */
class SettingsActivity : AppCompatActivity() {
    private val repo get() = Repository.get(this)

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        title = "設定"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fun section(titleText: String) {
            column.addView(textView(titleText, 16f, bold = true, color = colText()).apply {
                setPadding(0, dp(18), 0, dp(6))
            })
        }
        fun body(text: String) {
            column.addView(textView(text, 13f, color = colTextSub()))
        }
        fun matchWrap(top: Int = 8) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        // -- exploration share (§5b) -----------------------------------
        section("探索の割合")
        body("新しい話題や媒体をフィードに混ぜる割合です。次のフィード生成から適用されます。0%（探索なし）も選べます。")
        val shareLabel = textView(sizeSp = 15f, bold = true, color = colText()).apply { setPadding(0, dp(8), 0, 0) }
        val shareBar = SeekBar(this).apply {
            max = 10 // 0..50% in 5% steps
            progressTintList = android.content.res.ColorStateList.valueOf(colAccent())
            thumbTintList = android.content.res.ColorStateList.valueOf(colAccent())
            progress = repo.explorationShare() / 5
            layoutParams = matchWrap()
        }
        fun renderShare() { shareLabel.text = "探索割合: ${shareBar.progress * 5}%" }
        renderShare()
        shareBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { renderShare() }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                repo.setExplorationShare(bar.progress * 5)
                renderShare()
            }
        })
        column.addView(shareLabel); column.addView(shareBar)

        // -- backend connection ----------------------------------------
        section("バックエンド接続先")
        body("PythonバックエンドのURL（tailnet内アドレス）です。空欄の場合はモックデータで動作します。")
        val urlInput = EditText(this).apply {
            setText(repo.backendUrl())
            hint = "例: http://100.x.x.x:8765（空欄 = モック）"
            layoutParams = matchWrap()
        }
        column.addView(urlInput)
        val saveBtn = Button(this).apply { stylePrimary(this@SettingsActivity);
            text = "接続先を保存"
            layoutParams = matchWrap()
            setOnClickListener {
                repo.setBackendUrl(urlInput.text.toString())
                UndoBar.toast(column, if (repo.isFake()) "モックデータに切り替えました" else "接続先を保存しました")
            }
        }
        column.addView(saveBtn)

        // -- measurement note ------------------------------------------
        section("計測について")
        body("記事の滞留時間と読了率は記録されますが、推薦の計算には使いません（分析用にのみ保持されます）。")

        // -- version ----------------------------------------------------
        section("バージョン")
        val info = packageManager.getPackageInfo(packageName, 0)
        body("Discover ${info.versionName} (${info.longVersionCode})")
        body("未送信イベント: ${repo.queue.pendingCount()}件")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(colBg())
            addView(column)
        }
        applySystemBarInsets(scroll)
        setContentView(scroll)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
