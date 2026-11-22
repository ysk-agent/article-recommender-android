package dev.moriya.discover.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * stage 2 from 55%. The background colour and label switch at the
 * stage-2 boundary, and a haptic tick fires on every crossing, because
 * overshooting on the left flips the meaning (dislike -> known).
 * The action is decided by the stage reached at release.
 */
class TwoStageSwipe(
    private val onAction: (holder: RecyclerView.ViewHolder, action: SwipeAction) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var lastStage = 0
    private var lastFraction = 0f
    private var lastDirection = 0

    companion object {
        const val STAGE1 = 0.18f
        const val STAGE2 = 0.55f

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

        fun color(action: SwipeAction): Int = when (action) {
            SwipeAction.DISLIKE -> Color.rgb(198, 40, 40)
            SwipeAction.KNOWN -> Color.rgb(97, 97, 97)
            SwipeAction.INBOX -> Color.rgb(46, 125, 50)
            SwipeAction.INBOX_HIGH -> Color.rgb(21, 101, 192)
        }
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

    override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = STAGE1

    override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                             dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
        val item = vh.itemView
        val fraction = if (item.width > 0) abs(dX) / item.width else 0f
        val direction = if (dX < 0) -1 else if (dX > 0) 1 else 0
        val stage = stageOf(fraction)
        lastFraction = fraction; lastDirection = direction

        if (isActive && stage != lastStage && direction != 0) {
            // Boundary crossed: label/colour switch here, with a tick.
            item.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS)
            lastStage = stage
        }

        val action = actionFor(direction, stage)
        if (action != null) {
            val paint = Paint().apply { color = color(action) }
            if (direction < 0) c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), paint)
            else c.drawRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(), paint)
            val text = Paint().apply {
                color = Color.WHITE; textSize = 40f; isAntiAlias = true; textStyleBold(this)
            }
            val label = label(action)
            val ty = item.top + item.height / 2f + 14f
            if (direction < 0) c.drawText(label, item.right - text.measureText(label) - 32f, ty, text)
            else c.drawText(label, item.left + 32f, ty, text)
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
    }

    private fun textStyleBold(p: Paint) { p.isFakeBoldText = true }

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val dir = if (direction == ItemTouchHelper.LEFT) -1 else 1
        val action = actionFor(dir, stageOf(lastFraction).coerceAtLeast(1))
        lastStage = 0; lastFraction = 0f; lastDirection = 0
        if (action != null) onAction(vh, action)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        lastStage = 0; lastFraction = 0f; lastDirection = 0
    }
}
