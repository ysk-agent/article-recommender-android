package dev.moriya.discover.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.moriya.discover.data.SwipeAction
import kotlin.math.abs

/**
 * Two-stage swipe, both directions (design §3), with the stage-2
 * trigger redesigned at Yosuke's direction (2026-11-22) on the
 * pull-to-refresh model: distance alone never selects stage 2.
 *
 * - Past 18% of the row width, releasing applies stage 1.
 * - Past 55% the row is in the stage-2 zone: the stage-2 label shows
 *   dimmed. Holding there for ~0.45 s arms stage 2 — the label goes
 *   full-strength with a stronger haptic — and releasing then
 *   applies stage 2. Leaving the zone, or releasing before the hold
 *   completes, falls back to stage 1.
 *
 * History: v0.1.0 read the stage after the dismiss animation had run
 * the row off to 100%, so almost every swipe became stage 2.
 * v0.1.1 captured the stage at release and moved the boundary to
 * 70%, but a natural decisive flick still travels that far, so
 * stage 1 stayed hard to hit on the real device. The hold is the
 * deliberate-intent signal that distance cannot express.
 *
 * While the finger sits still in the zone, no new draw is triggered
 * by touch events, so a small invalidate ticker runs for exactly
 * that period to let the arming moment render.
 */
class TwoStageSwipe(
    private val onAction: (holder: RecyclerView.ViewHolder, action: SwipeAction) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var lastStage = 0
    private var releaseFraction = 0f
    private var releaseArmed = false
    private var zoneEnteredAt = 0L
    private var armed = false
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    companion object {
        const val STAGE1 = 0.18f
        const val STAGE2_ZONE = 0.55f
        const val HOLD_MS = 450L

        fun stageOf(fraction: Float): Int = if (fraction >= STAGE1) 1 else 0

        fun actionFor(direction: Int, stage: Int): SwipeAction? = when {
            stage <= 0 -> null
            direction < 0 && stage == 1 -> SwipeAction.DISLIKE
            direction < 0 -> SwipeAction.KNOWN
            direction > 0 && stage == 1 -> SwipeAction.INBOX
            direction > 0 -> SwipeAction.INBOX_HIGH
            else -> null
        }

        fun label(action: SwipeAction): String = when (action) {
            SwipeAction.DISLIKE -> "低評価"
            SwipeAction.KNOWN -> "もう知っている"
            SwipeAction.INBOX -> "Inboxへ"
            SwipeAction.INBOX_HIGH -> "Inbox＋高評価"
        }

        /** Calm, desaturated tones (v0.1.1 restyle). */
        fun color(action: SwipeAction): Int = when (action) {
            SwipeAction.DISLIKE -> Color.rgb(176, 84, 76)
            SwipeAction.KNOWN -> Color.rgb(133, 136, 140)
            SwipeAction.INBOX -> Color.rgb(95, 141, 110)
            SwipeAction.INBOX_HIGH -> Color.rgb(91, 125, 163)
        }
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

    override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = STAGE1

    // No fling dismissal: a fast short flick must not throw the row.
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE
    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    private fun startTicker(rv: RecyclerView) {
        if (ticker != null) return
        val t = object : Runnable {
            override fun run() {
                if (zoneEnteredAt == 0L) { ticker = null; return }
                rv.invalidate()
                handler.postDelayed(this, 50)
            }
        }
        ticker = t
        handler.postDelayed(t, 50)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun haptic(item: android.view.View, strong: Boolean) {
        item.performHapticFeedback(
            if (strong && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                             dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
        val item = vh.itemView
        val fraction = if (item.width > 0) abs(dX) / item.width else 0f
        val direction = if (dX < 0) -1 else if (dX > 0) 1 else 0

        if (isActive) {
            // Only the live drag counts; the post-release dismiss
            // animation runs through this same callback and must not
            // rewrite the captured state.
            releaseFraction = fraction
            val inZone = fraction >= STAGE2_ZONE && direction != 0
            if (inZone) {
                if (zoneEnteredAt == 0L) {
                    zoneEnteredAt = SystemClock.uptimeMillis()
                    startTicker(rv)
                }
                if (!armed && SystemClock.uptimeMillis() - zoneEnteredAt >= HOLD_MS) {
                    armed = true
                    haptic(item, strong = true)
                }
            } else {
                zoneEnteredAt = 0L
                armed = false
                stopTicker()
            }
            releaseArmed = armed

            // Display stage: 0 none, 1 = stage 1, 2 = stage 2 shown
            // dimmed (in zone, not yet armed), 3 = stage 2 armed.
            val display = when {
                fraction < STAGE1 || direction == 0 -> 0
                armed -> 3
                inZone -> 2
                else -> 1
            }
            if (display != lastStage && direction != 0 && fraction >= STAGE1) {
                haptic(item, strong = false)
                lastStage = display
            }
            if (direction != 0) drawReveal(c, rv, item, dX, direction,
                actionFor(direction, if (display >= 2) 2 else 1), dimmed = display == 2)
        } else if (direction != 0 && releaseFraction >= STAGE1) {
            // Dismiss animation: keep showing the action that will be
            // applied, at full strength.
            drawReveal(c, rv, item, dX, direction,
                actionFor(direction, if (releaseArmed) 2 else 1), dimmed = false)
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
    }

    private fun drawReveal(c: Canvas, rv: RecyclerView, item: android.view.View,
                           dX: Float, direction: Int, action: SwipeAction?, dimmed: Boolean) {
        if (action == null) return
        val alpha = if (dimmed) 120 else 255
        val radius = rv.context.dp(16).toFloat()
        val paint = Paint().apply { color = color(action); this.alpha = alpha; isAntiAlias = true }
        val rect = if (direction < 0)
            RectF(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat())
        else
            RectF(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
        c.drawRoundRect(rect, radius, radius, paint)
        val text = Paint().apply {
            color = Color.WHITE; this.alpha = alpha
            textSize = rv.context.dp(14).toFloat(); isAntiAlias = true; isFakeBoldText = true
        }
        val label = label(action)
        val ty = item.top + item.height / 2f + rv.context.dp(5)
        if (direction < 0) c.drawText(label, item.right - text.measureText(label) - rv.context.dp(16), ty, text)
        else c.drawText(label, item.left + rv.context.dp(16).toFloat(), ty, text)
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val dir = if (direction == ItemTouchHelper.LEFT) -1 else 1
        val stage = if (releaseArmed) 2 else 1
        val action = actionFor(dir, stage)
        resetGesture()
        if (action != null) onAction(vh, action)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        resetGesture()
    }

    private fun resetGesture() {
        lastStage = 0; releaseFraction = 0f; releaseArmed = false
        zoneEnteredAt = 0L; armed = false
        stopTicker()
    }
}
