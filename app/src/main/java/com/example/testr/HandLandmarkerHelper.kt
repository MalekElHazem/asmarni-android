package com.example.testr

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    private val context: Context,
    private val listener: HandLandmarkerListener
) {
    private var handLandmarker: HandLandmarker? = null

    interface HandLandmarkerListener {
        fun onResults(result: HandLandmarkerResult, input: MPImage)
        fun onError(error: String)
    }

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.7f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setNumHands(2)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, image ->
                    listener.onResults(result, image)
                }
                .setErrorListener { error ->
                    listener.onError(error.message.toString())
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Error initializing HandLandmarker: ${e.message}")
        }
    }

    fun detectLiveStream(image: MPImage, timestamp: Long) {
        handLandmarker?.detectAsync(image, timestamp)
    }

    fun clear() {
        handLandmarker?.close()
        handLandmarker = null
    }
}