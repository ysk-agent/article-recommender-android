package dev.moriya.discover

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
        // targetSdk 35 forces edge-to-edge on Android 15: without this,
        // content slides under the status bar / action bar and the tab
        // bar under the navigation bar (seen in the first CI run's
        // screenshots). Classic fitted layout is what this app wants.
        WindowCompat.setDecorFitsSystemWindows(window, true)
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(container); addView(bar)
        }
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
