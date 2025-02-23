package com.example.testr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var poseResults: PoseLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val smoothedLandmarks = mutableMapOf<Int, Pair<Float, Float>>()
    private val SMOOTHING_FACTOR = 0.8f  // Adjust between 0.5 - 0.9

    private fun smoothLandmark(index: Int, x: Float, y: Float): Pair<Float, Float> {
        val prev = smoothedLandmarks[index] ?: Pair(x, y)
        val newX = prev.first * SMOOTHING_FACTOR + x * (1 - SMOOTHING_FACTOR)
        val newY = prev.second * SMOOTHING_FACTOR + y * (1 - SMOOTHING_FACTOR)
        smoothedLandmarks[index] = Pair(newX, newY)
        return Pair(newX, newY)
    }


    init {
        initPaints()
    }

    fun clear() {
        results = null
        poseResults = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { handLandmarkerResult ->
            val effectiveImageWidth = imageWidth * scaleFactor
            val offsetX = (width - effectiveImageWidth) / 2f

            handLandmarkerResult.landmarks().forEach { landmarks ->
                landmarks.forEach { normalizedLandmark ->
                    val flippedX = offsetX + effectiveImageWidth - (normalizedLandmark.x() * effectiveImageWidth)
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor
                    canvas.drawPoint(flippedX, y, pointPaint)
                }

                HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                    val startLandmark = landmarks[connection.start()]
                    val endLandmark = landmarks[connection.end()]

                    val startX = offsetX + effectiveImageWidth - (startLandmark.x() * effectiveImageWidth)
                    val startY = startLandmark.y() * imageHeight * scaleFactor
                    val endX = offsetX + effectiveImageWidth - (endLandmark.x() * effectiveImageWidth)
                    val endY = endLandmark.y() * imageHeight * scaleFactor

                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
            }
        }

        // Pose landmarks
        poseResults?.let { result ->
            val effectiveImageWidth = imageWidth * scaleFactor
            val offsetX = (width - effectiveImageWidth) / 2f

            result.landmarks().forEach { landmarkList ->
                // Draw landmarks
                landmarkList.forEachIndexed { index, normalizedLandmark ->
                    val flippedX = offsetX + effectiveImageWidth - (normalizedLandmark.x() * effectiveImageWidth)
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor
                    val (smoothX, smoothY) = smoothLandmark(index, flippedX, y)
                    canvas.drawPoint(smoothX, smoothY, pointPaint)

                }

                // Draw connections
                POSE_CONNECTIONS.forEach { connection ->
                    val startIdx = connection.first
                    val endIdx = connection.second
                    if (startIdx < landmarkList.size && endIdx < landmarkList.size) {
                        val startLandmark = landmarkList[startIdx]
                        val endLandmark = landmarkList[endIdx]

                        val startX = offsetX + effectiveImageWidth - (startLandmark.x() * effectiveImageWidth)
                        val startY = startLandmark.y() * imageHeight * scaleFactor
                        val endX = offsetX + effectiveImageWidth - (endLandmark.x() * effectiveImageWidth)
                        val endY = endLandmark.y() * imageHeight * scaleFactor
                        canvas.drawLine(startX, startY, endX, endY, linePaint)
                    }
                }
            }
        }

    }

    fun setHandResults(
        result: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        this.results = result
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM -> max(width * 1f / imageWidth, height * 1f / imageHeight)
        }
        invalidate()
    }

    fun setPoseResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        this.poseResults = poseLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM -> max(width * 1f / imageWidth, height * 1f / imageHeight)
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F

        // Pose landmark connections for full body
        private val POSE_CONNECTIONS = listOf(
            // Face connections
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), // Left eye to ear
            Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8), // Right eye to ear
            Pair(9, 10), // Mouth left to right

            // Upper body connections
            Pair(11, 12), // Shoulders
            Pair(11, 13), Pair(13, 15), // Left arm: Shoulder -> Elbow -> Wrist
            Pair(15, 17), Pair(15, 19), Pair(15, 21), // Left wrist to pinky, index, thumb
            Pair(17, 19), Pair(19, 21), // Left fingers
            Pair(12, 14), Pair(14, 16), // Right arm: Shoulder -> Elbow -> Wrist
            Pair(16, 18), Pair(16, 20), Pair(16, 22), // Right wrist to pinky, index, thumb
            Pair(18, 20), Pair(20, 22), // Right fingers

            // Lower body connections
            Pair(11, 23), Pair(12, 24), // Shoulders to hips
            Pair(23, 24), // Hips
            Pair(23, 25), Pair(25, 27), // Left hip -> knee -> ankle
            Pair(27, 29), Pair(27, 31), // Left ankle -> heel, foot index
            Pair(29, 31), // Left heel to foot index
            Pair(24, 26), Pair(26, 28), // Right hip -> knee -> ankle
            Pair(28, 30), Pair(28, 32), // Right ankle -> heel, foot index
            Pair(30, 32) // Right heel to foot index
        )

    }
}
