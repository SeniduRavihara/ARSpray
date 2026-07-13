// com/g37/arspray/ar/SprayEngine.kt
package com.g37.arspray.ar

import kotlin.math.sqrt

/**
 * Computes interpolated screen-space points between two consecutive touch positions
 * and calls [onDrawPoint] for each one, producing a continuous spray stroke.
 *
 * @param startX       X screen coordinate of the previous touch event (null = start of stroke)
 * @param startY       Y screen coordinate of the previous touch event (null = start of stroke)
 * @param endX         X screen coordinate of the current touch event
 * @param endY         Y screen coordinate of the current touch event
 * @param timeDelta    Milliseconds elapsed since the last touch event.
 *                     If > 150 ms, the stroke is treated as a new segment (no interpolation).
 * @param onDrawPoint  Callback invoked with the (x, y) of each interpolated point.
 */
fun interpolateAndDraw(
    startX: Float?,
    startY: Float?,
    endX: Float,
    endY: Float,
    timeDelta: Long,
    onDrawPoint: (Float, Float) -> Unit
) {
    // New stroke or long pause – just place the current point
    if (startX == null || startY == null || timeDelta > STROKE_BREAK_THRESHOLD_MS) {
        onDrawPoint(endX, endY)
        return
    }

    val dx = endX - startX
    val dy = endY - startY
    val distance = sqrt(dx * dx + dy * dy)

    // Adaptive step: at most MAX_STEPS interpolated points, minimum step 10 px
    val stepSize = maxOf(MIN_STEP_PX, distance / MAX_STEPS)
    val steps = (distance / stepSize).toInt()

    if (steps > 0) {
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            onDrawPoint(startX + dx * t, startY + dy * t)
        }
    } else {
        onDrawPoint(endX, endY)
    }
}

// Time gap (ms) that starts a new, unconnected stroke segment
private const val STROKE_BREAK_THRESHOLD_MS = 150L

// Minimum pixel distance between interpolated points
private const val MIN_STEP_PX = 5f

// Maximum interpolated points generated per touch event
private const val MAX_STEPS = 30f
