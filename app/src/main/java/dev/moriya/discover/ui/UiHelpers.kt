package dev.moriya.discover.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.textView(text: String = "", sizeSp: Float = 14f, bold: Boolean = false,
                     color: Int? = null, maxLines: Int = Int.MAX_VALUE): TextView =
    TextView(this).apply {
        this.text = text; textSize = sizeSp
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        color?.let { setTextColor(it) }
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

/**
 * Undo banner (design §3: after a swipe, show the applied action with
 * an undo option). A bottom bar inside the fragment's root that
 * auto-dismisses; replaces Snackbar so the app needs no Material dep.
 */
object UndoBar {
    private val handler = Handler(Looper.getMainLooper())

    fun show(root: ViewGroup, message: String, undoLabel: String = "取り消し",
             durationMs: Long = 5000, onUndo: () -> Unit) {
        val ctx = root.context
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(38, 38, 38))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(8), ctx.dp(10))
        }
        val label = ctx.textView(message, 14f, color = Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val button = Button(ctx).apply {
            text = undoLabel
            setTextColor(Color.rgb(144, 202, 249))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                handler.removeCallbacksAndMessages(bar)
                (bar.parent as? ViewGroup)?.removeView(bar)
                onUndo()
            }
        }
        bar.addView(label); bar.addView(button)
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(ctx.dp(8), 0, ctx.dp(8), ctx.dp(8))
        root.addView(bar, lp)
        handler.postDelayed({ (bar.parent as? ViewGroup)?.removeView(bar) }, durationMs)
    }

    fun toast(root: ViewGroup, message: String) {
        android.widget.Toast.makeText(root.context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
