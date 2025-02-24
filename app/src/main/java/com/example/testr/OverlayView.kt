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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var poseResults: PoseLandmarkerResult? = null
    private var faceResults: FaceLandmarkerResult? = null

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

        pointPaint.color = Color.GREEN
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
                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    val startIdx = connection.start()
                    val endIdx = connection.end()
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

        // Update the face drawing section in OverlayView.kt
        faceResults?.let { result ->
            val effectiveImageWidth = imageWidth * scaleFactor
            val offsetX = (width - effectiveImageWidth) / 2f

            result.faceLandmarks().forEach { faceLandmarks ->
                // Draw points
                faceLandmarks.forEach { landmark ->
                    val flippedX = offsetX + effectiveImageWidth - (landmark.x() * effectiveImageWidth)
                    val y = landmark.y() * imageHeight * scaleFactor
                    canvas.drawPoint(flippedX, y, pointPaint)
                }

                // Draw face connections
                FaceLandmarker.FACE_LANDMARKS_CONNECTORS.forEach { connection ->
                    val start = connection.start()
                    val end = connection.end()
                    if (start < faceLandmarks.size && end < faceLandmarks.size) {
                        val startLandmark = faceLandmarks[start]
                        val endLandmark = faceLandmarks[end]

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

    fun setFaceResults(
        faceLandmarkerResult: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        this.faceResults = faceLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
            RunningMode.LIVE_STREAM -> max(width * 1f / imageWidth, height * 1f / imageHeight)
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 6F
    }
}
