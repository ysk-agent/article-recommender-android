package dev.moriya.discover.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.moriya.discover.data.SwipeAction
import kotlin.math.abs

/**
 * Two-stage swipe, both directions (design §3).
 *
 * Stage boundaries are fractions of the row width: stage 1 from 18%,
 * stage 2 from 70%. The stage is captured at the moment the finger is
 * released — the dismiss animation afterwards must not count, or its
 * run-off to 100% would promote nearly every swipe to stage 2 (the
 * v0.1.0 defect Yosuke reported: stage 1 was almost unreachable).
 * Fling dismissal is disabled for the same reason: distance alone
 * decides. The background colour and label switch at the stage-2
 * boundary, and a haptic tick fires on every crossing, because
 * overshooting on the left flips the meaning (dislike -> known).
 */
class TwoStageSwipe(
    private val onAction: (holder: RecyclerView.ViewHolder, action: SwipeAction) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var lastStage = 0
    private var releaseFraction = 0f
    private var releaseDirection = 0

    companion object {
        const val STAGE1 = 0.18f
        const val STAGE2 = 0.70f

        fun stageOf(fraction: Float): Int = when {
            fraction >= STAGE2 -> 2
            fraction >= STAGE1 -> 1
            else -> 0
        }

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

    override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                             dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
        val item = vh.itemView
        val fraction = if (item.width > 0) abs(dX) / item.width else 0f
        val direction = if (dX < 0) -1 else if (dX > 0) 1 else 0
        val stage = stageOf(fraction)

        if (isActive) {
            // Only the live drag counts. After release, ItemTouchHelper
            // animates the row off-screen through this same callback;
            // that run-off must not rewrite the captured stage.
            releaseFraction = fraction
            releaseDirection = direction
            if (stage != lastStage && direction != 0) {
                // Boundary crossed: label/colour switch here, with a tick.
                item.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.LONG_PRESS)
                lastStage = stage
            }
        }

        val action = actionFor(direction, stage)
        if (action != null && direction != 0) {
            val radius = rv.context.dp(16).toFloat()
            val paint = Paint().apply { color = color(action); isAntiAlias = true }
            val rect = if (direction < 0)
                RectF(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat())
            else
                RectF(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
            c.drawRoundRect(rect, radius, radius, paint)
            val text = Paint().apply {
                color = Color.WHITE; textSize = rv.context.dp(14).toFloat(); isAntiAlias = true
                isFakeBoldText = true
            }
            val label = label(action)
            val ty = item.top + item.height / 2f + rv.context.dp(5)
            if (direction < 0) c.drawText(label, item.right - text.measureText(label) - rv.context.dp(16), ty, text)
            else c.drawText(label, item.left + rv.context.dp(16).toFloat(), ty, text)
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val dir = if (direction == ItemTouchHelper.LEFT) -1 else 1
        val action = actionFor(dir, stageOf(releaseFraction).coerceAtLeast(1))
        lastStage = 0; releaseFraction = 0f; releaseDirection = 0
        if (action != null) onAction(vh, action)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        lastStage = 0; releaseFraction = 0f; releaseDirection = 0
    }
}
