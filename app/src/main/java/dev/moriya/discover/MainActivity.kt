package dev.moriya.discover

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import dev.moriya.discover.ui.FeedFragment
import dev.moriya.discover.ui.HistoryFragment
import dev.moriya.discover.ui.InboxFragment
import dev.moriya.discover.ui.SourcesFragment
import dev.moriya.discover.ui.dp

/** Four tabs (design §2): Feed / Inbox / Sources / History.
 *  The tab bar is a plain button row — the app builds its UI in code,
 *  like the sibling apps, so no Material / navigation components. */
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
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.forEachIndexed { index, (label, _) ->
            val btn = Button(this).apply { isAllCaps = false;
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { select(index) }
            }
            tabButtons.add(btn); bar.addView(btn)
        }
        // Settings is a separate screen, not a fifth tab (design §2
        // fixes four tabs); its button sits at the end of the tab bar.
        bar.addView(Button(this).apply { isAllCaps = false;
            text = "設定"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f)
            setOnClickListener { startActivity(dev.moriya.discover.ui.SettingsActivity.intent(this@MainActivity)) }
        })
        // NoActionBar theme (edge-to-edge under targetSdk 35 makes the
        // AppCompat action bar overlay the content), so the app draws
        // its own title bar, like the sibling apps draw their headers.
        val titleBar = android.widget.TextView(this).apply {
            text = "Discover"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#5D4037"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleBar); addView(container); addView(bar)
        }
        dev.moriya.discover.ui.applySystemBarInsets(root)
        setContentView(root)
        if (saved == null) select(0)
    }

    private fun select(index: Int) {
        tabButtons.forEachIndexed { i, btn ->
            btn.typeface = if (i == index) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        supportFragmentManager.beginTransaction()
            .replace(CONTAINER_ID, tabs[index].second())
            .commit()
    }

    companion object { const val CONTAINER_ID = 0xD15C }
}
