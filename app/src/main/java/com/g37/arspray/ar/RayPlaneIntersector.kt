// com/g37/arspray/ar/RayPlaneIntersector.kt
package com.g37.arspray.ar

import com.google.ar.core.Frame
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.normalize

/**
 * Performs camera-ray to whiteboard-plane intersection in AR space.
 *
 * Instead of relying on ARCore's [Frame.hitTest] (which only detects real-world
 * surfaces like floors and walls), this utility casts a ray from the camera
 * through a screen touch point and intersects it with the whiteboard's own
 * geometric plane. This produces frame-perfect, wobble-free drawing coordinates.
 */
object RayPlaneIntersector {

    /**
     * Pre-computed ray-casting context for batched drawing operations.
     *
     * **Why this exists**: Each touch-move event generates up to 15 interpolated
     * points via [com.g37.arspray.ar.interpolateAndDraw]. Without this cache,
     * every single point would recompute `inverse(projection × view)` — an O(n³)
     * matrix inversion that, at 60 Hz × 15 points = 900 inversions/sec, locks up
     * the UI thread and crashes the app.
     *
     * Create one [Session] per touch event, then call [intersectLocal] for each
     * interpolated screen point.  Only cheap per-point math runs inside.
     *
     * @param frame              Current ARCore frame (camera matrices snapshot).
     * @param viewWidth          AR view width in pixels.
     * @param viewHeight         AR view height in pixels.
     * @param boardWorldPosition Whiteboard center in world coordinates.
     * @param boardWorldTransform Whiteboard's full 4×4 world transform.
     */
    class Session(
        frame: Frame,
        viewWidth: Int,
        viewHeight: Int,
        boardWorldPosition: Float3,
        boardWorldTransform: Mat4
    ) {
        // ── Expensive values computed ONCE in init ──
        private val rayOrigin: Float3
        private val invViewProj: Mat4
        private val boardCenter: Float3
        private val boardNormal: Float3
        private val boardWorldToLocal: Mat4
        private val vw: Float = viewWidth.toFloat()
        private val vh: Float = viewHeight.toFloat()

        init {
            val pose = frame.camera.displayOrientedPose
            rayOrigin = Float3(pose.tx(), pose.ty(), pose.tz())

            val viewArr = FloatArray(16)
            val projArr = FloatArray(16)
            frame.camera.getViewMatrix(viewArr, 0)
            frame.camera.getProjectionMatrix(projArr, 0, NEAR_CLIP, FAR_CLIP)

            invViewProj = inverse(columnMajorToMat4(projArr) * columnMajorToMat4(viewArr))

            boardCenter = boardWorldPosition
            boardNormal = normalize(
                Float3(
                    boardWorldTransform[2].x,
                    boardWorldTransform[2].y,
                    boardWorldTransform[2].z
                )
            )
            boardWorldToLocal = inverse(boardWorldTransform)
        }

        /**
         * Returns the whiteboard-local `(x, y, z)` for the given screen point,
         * or `null` if the ray is parallel to the board or points away.
         *
         * This is **cheap** — just NDC conversion, one mat-vec multiply,
         * normalize, dot, and one more mat-vec multiply.
         */
        fun intersectLocal(screenX: Float, screenY: Float): Float3? {
            val ndcX = 2f * screenX / vw - 1f
            val ndcY = 1f - 2f * screenY / vh

            val wp4 = invViewProj * Float4(ndcX, ndcY, -1f, 1f)
            if (wp4.w == 0f) return null
            val worldPt = Float3(wp4.x / wp4.w, wp4.y / wp4.w, wp4.z / wp4.w)

            val rayDir = normalize(worldPt - rayOrigin)
            val denom = dot(rayDir, boardNormal)
            if (kotlin.math.abs(denom) < PARALLEL_EPSILON) return null

            val t = dot(boardCenter - rayOrigin, boardNormal) / denom
            if (t < 0f) return null

            val worldHit = Float3(
                rayOrigin.x + rayDir.x * t,
                rayOrigin.y + rayDir.y * t,
                rayOrigin.z + rayDir.z * t
            )

            val local4 = boardWorldToLocal * Float4(worldHit, 1f)
            return Float3(local4.x, local4.y, local4.z)
        }
    }

    // ── Single-shot convenience method (for tap-to-place objects) ────────

    /**
     * One-shot ray-cast for single tap events (e.g. placing an object).
     * For batched drawing, use [Session] instead.
     */
    fun intersect(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewWidth: Int,
        viewHeight: Int,
        boardWorldPosition: Float3,
        boardWorldTransform: Mat4
    ): Float3? {
        val pose = frame.camera.displayOrientedPose
        val rayOrigin = Float3(pose.tx(), pose.ty(), pose.tz())

        val viewArr = FloatArray(16)
        val projArr = FloatArray(16)
        frame.camera.getViewMatrix(viewArr, 0)
        frame.camera.getProjectionMatrix(projArr, 0, NEAR_CLIP, FAR_CLIP)

        val invViewProj = inverse(
            columnMajorToMat4(projArr) * columnMajorToMat4(viewArr)
        )

        val ndcX = 2f * screenX / viewWidth - 1f
        val ndcY = 1f - 2f * screenY / viewHeight

        val wp4 = invViewProj * Float4(ndcX, ndcY, -1f, 1f)
        if (wp4.w == 0f) return null
        val worldPt = Float3(wp4.x / wp4.w, wp4.y / wp4.w, wp4.z / wp4.w)

        val rayDir = normalize(worldPt - rayOrigin)

        val boardNormal = normalize(
            Float3(
                boardWorldTransform[2].x,
                boardWorldTransform[2].y,
                boardWorldTransform[2].z
            )
        )

        val denom = dot(rayDir, boardNormal)
        if (kotlin.math.abs(denom) < PARALLEL_EPSILON) return null

        val t = dot(boardWorldPosition - rayOrigin, boardNormal) / denom
        if (t < 0f) return null

        return Float3(
            rayOrigin.x + rayDir.x * t,
            rayOrigin.y + rayDir.y * t,
            rayOrigin.z + rayDir.z * t
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun columnMajorToMat4(a: FloatArray): Mat4 = Mat4(
        Float4(a[0], a[1], a[2], a[3]),
        Float4(a[4], a[5], a[6], a[7]),
        Float4(a[8], a[9], a[10], a[11]),
        Float4(a[12], a[13], a[14], a[15])
    )

    private const val NEAR_CLIP = 0.1f
    private const val FAR_CLIP = 100f
    private const val PARALLEL_EPSILON = 1e-6f
}
