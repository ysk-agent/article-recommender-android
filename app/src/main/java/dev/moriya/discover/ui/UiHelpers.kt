package dev.moriya.discover.ui

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
            background = ctx.roundedBg(Color.rgb(35, 39, 46), 12)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(8), ctx.dp(10))
        }
        val label = ctx.textView(message, 14f, color = Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val button = Button(ctx).apply { isAllCaps = false;
            text = undoLabel
            setTextColor(Color.rgb(168, 195, 207))
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
        lp.setMargins(ctx.dp(12), 0, ctx.dp(12), ctx.dp(12))
        root.addView(bar, lp)
        handler.postDelayed({ (bar.parent as? ViewGroup)?.removeView(bar) }, durationMs)
    }

    fun toast(root: ViewGroup, message: String) {
        android.widget.Toast.makeText(root.context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}


// ---------------------------------------------------------------------
// Calm modern palette (v0.1.1 restyle, direction chosen by Yosuke:
// subdued tones, following the system light/dark setting). Warm-grey
// neutrals, one muted slate accent, no saturated primaries.

fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

fun Context.colBg(): Int = if (isNight()) Color.rgb(16, 18, 20) else Color.rgb(247, 247, 244)
fun Context.colSurface(): Int = if (isNight()) Color.rgb(27, 30, 35) else Color.WHITE
fun Context.colBorder(): Int = if (isNight()) Color.rgb(42, 46, 53) else Color.rgb(231, 229, 224)
fun Context.colText(): Int = if (isNight()) Color.rgb(236, 236, 234) else Color.rgb(28, 27, 26)
fun Context.colTextSub(): Int = if (isNight()) Color.rgb(155, 161, 166) else Color.rgb(111, 110, 106)
fun Context.colAccent(): Int = if (isNight()) Color.rgb(144, 164, 174) else Color.rgb(84, 110, 122)
fun Context.colAccentTint(): Int = if (isNight()) Color.rgb(35, 42, 46) else Color.rgb(232, 237, 239)

fun Context.roundedBg(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

/** Filled primary button: accent background, white label, soft corners. */
fun Button.stylePrimary(ctx: Context) {
    background = ctx.roundedBg(ctx.colAccent(), 12)
    setTextColor(Color.WHITE)
    isAllCaps = false
}

/** Quiet secondary button: surface background with a hairline border. */
fun Button.styleOutline(ctx: Context) {
    background = ctx.roundedBg(ctx.colSurface(), 12, ctx.colBorder())
    setTextColor(ctx.colText())
    isAllCaps = false
}

/** targetSdk 35 draws edge-to-edge: pad [root] by the system bars so
 *  content never slides under the status bar or the navigation bar
 *  (CI runs 1-2: rows under the action bar, tab bar under the nav bar). */
fun applySystemBarInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
