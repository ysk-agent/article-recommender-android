package dev.moriya.discover.ui

import android.content.Context

/**
 * TEMPORARY debug tuning (v0.1.3, at Yosuke's direction 2026-11-22):
 * the stage-2 zone threshold and the hold-to-arm duration, adjustable
 * on the device from the slider bar at the top of MainActivity while
 * the hold-to-arm feel is being dialled in. TwoStageSwipe reads these
 * live on every draw, so a change applies to the next swipe with no
 * restart. Remove together with the slider bar in MainActivity.
 */
object DebugTuning {
    private const val PREFS = "debug_tuning"
    private const val KEY_ZONE_PCT = "stage2_zone_pct"
    private const val KEY_HOLD_MS = "hold_ms"

    // 35% / 400 ms: with the colour-blend display a wider zone
    // feels natural too (Yosuke, 2026-11-22).
    const val DEFAULT_ZONE_PCT = 35
    const val DEFAULT_HOLD_MS = 400
    const val ZONE_MIN = 30
    const val ZONE_MAX = 85
    const val HOLD_MIN = 100
    const val HOLD_MAX = 1200
    const val HOLD_STEP = 50

    fun zonePct(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ZONE_PCT, DEFAULT_ZONE_PCT)

    fun holdMs(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_HOLD_MS, DEFAULT_HOLD_MS)

    fun setZonePct(ctx: Context, pct: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ZONE_PCT, pct).apply()

    fun setHoldMs(ctx: Context, ms: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_HOLD_MS, ms).apply()
}
