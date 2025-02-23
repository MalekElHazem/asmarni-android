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
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { handLandmarkerResult ->

            // Compute the effective width of the drawn image and horizontal offset.
            val effectiveImageWidth = imageWidth * scaleFactor
            val offsetX = (width - effectiveImageWidth) / 2f

            // Draw all hands
            handLandmarkerResult.landmarks().forEachIndexed { handIndex, landmarks ->
                // Draw landmarks
                landmarks.forEach { normalizedLandmark ->
                    val flippedX = offsetX + effectiveImageWidth - (normalizedLandmark.x() * effectiveImageWidth)
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor
                    canvas.drawPoint(flippedX, y, pointPaint)
                }

                // Draw connections
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
    }

    fun setResults(
        handLandmarkerResults: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}