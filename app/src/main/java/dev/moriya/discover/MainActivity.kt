package dev.moriya.discover

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import dev.moriya.discover.ui.FeedFragment
import dev.moriya.discover.ui.HistoryFragment
import dev.moriya.discover.ui.InboxFragment
import dev.moriya.discover.ui.SourcesFragment
import dev.moriya.discover.ui.applySystemBarInsets
import dev.moriya.discover.ui.colAccent
import dev.moriya.discover.ui.colAccentTint
import dev.moriya.discover.ui.colBg
import dev.moriya.discover.ui.colBorder
import dev.moriya.discover.ui.colSurface
import dev.moriya.discover.ui.colText
import dev.moriya.discover.ui.colTextSub
import dev.moriya.discover.ui.dp
import dev.moriya.discover.ui.roundedBg

/** Four tabs (design §2): Feed / Inbox / Sources / History.
 *  The tab bar is a plain button row — the app builds its UI in code,
 *  like the sibling apps, so no Material / navigation components.
 *  v0.1.1 restyle: calm palette, background-blended header, and a
 *  pill highlight on the selected tab instead of stock buttons. */
class MainActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private val tabButtons = mutableListOf<Button>()
    private val tabs: List<Pair<String, () -> Fragment>> = listOf(
        "フィード" to { FeedFragment() },
        "Inbox" to { InboxFragment() },
        "媒体" to { SourcesFragment() },
        "履歴" to { HistoryFragment() },
    )

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        container = FrameLayout(this).apply {
            id = CONTAINER_ID
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colSurface())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        tabs.forEachIndexed { index, (label, _) ->
            val btn = Button(this).apply {
                isAllCaps = false
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { select(index) }
            }
            tabButtons.add(btn); bar.addView(btn)
        }
        // Settings is a separate screen, not a fifth tab (design §2
        // fixes four tabs); its button sits at the end of the tab bar.
        bar.addView(Button(this).apply {
            isAllCaps = false
            text = "設定"
            setTextColor(colTextSub())
            background = roundedBg(Color.TRANSPARENT, 24)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f)
            setOnClickListener { startActivity(dev.moriya.discover.ui.SettingsActivity.intent(this@MainActivity)) }
        })
        val hairline = View(this).apply {
            setBackgroundColor(colBorder())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        // NoActionBar theme (edge-to-edge under targetSdk 35 makes the
        // AppCompat action bar overlay the content), so the app draws
        // its own header: just the wordmark on the background colour.
        val titleBar = TextView(this).apply {
            text = "Discover"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colText())
            setPadding(dp(20), dp(18), dp(20), dp(10))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg())
            addView(titleBar); addView(container); addView(hairline); addView(bar)
        }
        applySystemBarInsets(root)
        setContentView(root)
        if (saved == null) select(0)
    }

    private fun select(index: Int) {
        tabButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.background = roundedBg(colAccentTint(), 24)
                btn.setTextColor(colAccent())
                btn.typeface = Typeface.DEFAULT_BOLD
            } else {
                btn.background = roundedBg(Color.TRANSPARENT, 24)
                btn.setTextColor(colTextSub())
                btn.typeface = Typeface.DEFAULT
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(CONTAINER_ID, tabs[index].second())
            .commit()
    }

    companion object { const val CONTAINER_ID = 0xD15C }
}
