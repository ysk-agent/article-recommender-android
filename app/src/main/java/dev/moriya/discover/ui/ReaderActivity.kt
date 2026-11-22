package dev.moriya.discover.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import dev.moriya.discover.data.Article
import dev.moriya.discover.data.Repository

/**
 * In-app article reader (WebView) — decided by Yosuke, 2026-11-22.
 *
 * Dwell time and completion rate are measured here and attached to the
 * OPEN event when the reader closes (design §5): they are logged, but
 * NOT used as model signals. Completion is the furthest scroll
 * position reached, as a fraction of the page height.
 */
class ReaderActivity : AppCompatActivity() {
    private val repo get() = Repository.get(this)
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var articleId: String? = null
    private var sourceId: String? = null
    private var startedAt = 0L
    private var maxCompletion = 0.0
    private var recorded = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        articleId = intent.getStringExtra("articleId")
        sourceId = intent.getStringExtra("sourceId")
        val url = intent.getStringExtra("url")!!
        title = intent.getStringExtra("title") ?: ""

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(colAccent())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { updateCompletion() }
            }
            setOnScrollChangeListener { _, _, _, _, _ -> updateCompletion() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg())
            addView(progress); addView(webView)
        }
        applySystemBarInsets(root)
        setContentView(root)
        startedAt = System.currentTimeMillis()
        webView.loadUrl(url)
    }

    private fun updateCompletion() {
        val contentPx = webView.contentHeight * webView.scale
        val viewPx = webView.height.toFloat()
        if (contentPx > viewPx) {
            val frac = ((webView.scrollY + viewPx) / contentPx).toDouble().coerceIn(0.0, 1.0)
            if (frac > maxCompletion) maxCompletion = frac
        } else if (contentPx > 0) {
            maxCompletion = 1.0
        }
        progress.progress = (maxCompletion * 100).toInt()
    }

    private fun recordOpen() {
        if (recorded) return
        recorded = true
        val id = articleId ?: return
        // OPEN carries dwell + completion (logged, not model signals).
        repo.open(
            Article(id, sourceId ?: "", "", title?.toString() ?: "", "", null, 0, ""),
            dwellMs = System.currentTimeMillis() - startedAt,
            completionRate = maxCompletion,
        )
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        recordOpen()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context, article: Article): Intent =
            Intent(context, ReaderActivity::class.java).apply {
                putExtra("articleId", article.id)
                putExtra("sourceId", article.sourceId)
                putExtra("url", article.url)
                putExtra("title", article.title)
            }

        fun open(context: Context, article: Article) {
            context.startActivity(intent(context, article))
        }
    }
}
