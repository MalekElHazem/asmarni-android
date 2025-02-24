package com.example.testr

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: FaceLandmarkerListener
) {
    private var faceLandmarker: FaceLandmarker? = null

    interface FaceLandmarkerListener {
        fun onResults(result: FaceLandmarkerResult, input: MPImage)
        fun onError(error: String)
    }

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task") // Different model file
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.7f)  // Face-specific confidence
                .setMinTrackingConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setNumFaces(1)  // Typically detect 1 face
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, image ->
                    listener.onResults(result, image)
                }
                .setErrorListener { error ->
                    listener.onError(error.message.toString())
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Error initializing FaceLandmarker: ${e.message}")
        }
    }

    fun detectLiveStream(image: MPImage, timestamp: Long) {
        faceLandmarker?.detectAsync(image, timestamp)
    }

    fun clear() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}