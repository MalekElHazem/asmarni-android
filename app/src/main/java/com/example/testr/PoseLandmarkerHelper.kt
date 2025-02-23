package com.example.testr

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    private val context: Context,
    private val listener: PoseLandmarkerListener
) {
    interface PoseLandmarkerListener {
        fun onResults(result: PoseLandmarkerResult, input: MPImage)
        fun onError(error: String)
    }

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")  // Ensure this file is in assets
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                // Adjust these confidence thresholds as needed.
                .setMinPoseDetectionConfidence(0.7f)
                .setMinPosePresenceConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, image ->
                    listener.onResults(result, image)
                }
                .setErrorListener { error ->
                    listener.onError(error.message ?: "Unknown error")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Error initializing PoseLandmarker: ${e.message}")
        }
    }

    fun detectLiveStream(image: MPImage, timestamp: Long) {
        poseLandmarker?.detectAsync(image, timestamp)
    }

    fun clear() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
