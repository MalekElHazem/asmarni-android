package com.example.testr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(),
    HandLandmarkerHelper.HandLandmarkerListener,
    PoseLandmarkerHelper.PoseLandmarkerListener,
    FaceLandmarkerHelper.FaceLandmarkerListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var interpreter: Interpreter

    // Variables for sign language detection (analogous to your Python code)
    private val sequence = mutableListOf<FloatArray>()
    private val sentence = mutableListOf<String>()
    private val predictions = mutableListOf<Int>()
    private val threshold = 0.3f
    private val actions = listOf("hello", "thanks", "iloveyou") // Update with your actual actions

    // Latest landmark results from detectors (these are lists of NormalizedLandmark objects)
    private var lastHandLandmarks: List<List<NormalizedLandmark>>? = null
    private var lastPoseLandmarks: List<List<NormalizedLandmark>>? = null
    private var lastFaceLandmarks: List<List<NormalizedLandmark>>? = null

    // Camera configuration
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.camera_view)
        overlayView = findViewById(R.id.overlay_view)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permissions
        @androidx.camera.core.ExperimentalGetImage
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Load the TFLite model from assets
        interpreter = Interpreter(loadModelFile())

        // Initialize your Mediapipe detection helpers
        handLandmarkerHelper = HandLandmarkerHelper(context = this, listener = this)
        poseLandmarkerHelper = PoseLandmarkerHelper(context = this, listener = this)
        faceLandmarkerHelper = FaceLandmarkerHelper(context = this, listener = this)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("actionv1lite_withops.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // Start the camera with an image analyzer that sends frames to all detectors.
    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview use case.
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Image analysis use case.
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy)
                            val rotatedBitmap = BitmapUtils.rotateBitmap(
                                bitmap,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                            val timestamp = SystemClock.uptimeMillis()

                            // Send the frame to all detectors.
                            handLandmarkerHelper.detectLiveStream(mpImage, timestamp)
                            poseLandmarkerHelper.detectLiveStream(mpImage, timestamp)
                            faceLandmarkerHelper.detectLiveStream(mpImage, timestamp)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Callback for HandLandmarker results.
    override fun onResults(result: HandLandmarkerResult, input: MPImage) {
        runOnUiThread {
            overlayView.setHandResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
        }
        lastHandLandmarks = result.landmarks()  // returns List<List<NormalizedLandmark>>
        processLatestLandmarks()
    }

    // Callback for PoseLandmarker results.
    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            overlayView.setPoseResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
        }
        lastPoseLandmarks = result.landmarks()
        processLatestLandmarks()
    }

    // Callback for FaceLandmarker results.
    override fun onResults(result: FaceLandmarkerResult, input: MPImage) {
        runOnUiThread {
            overlayView.setFaceResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
        }
        lastFaceLandmarks = result.faceLandmarks()
        processLatestLandmarks()
    }

    /**
     * Converts a list of lists of NormalizedLandmark objects into a flat list of floats.
     * Each NormalizedLandmark is assumed to have x, y, and z values.
     */
    private fun convertLandmarks(landmarks: List<List<NormalizedLandmark>>?): List<Float> {
        if (landmarks == null) return emptyList()
        return landmarks.flatMap { landmarkList ->
            landmarkList.mapNotNull { landmark ->
                // Skip null landmarks
                if (landmark == null) return@mapNotNull null
                listOf(
                    landmark.x().toFloat(),
                    landmark.y().toFloat(),
                    landmark.z().toFloat()
                )
            }.flatten()
        }
    }

    private fun convertPoseLandmarks(landmarks: List<List<NormalizedLandmark>>?): List<Float> {
        if (landmarks == null) return emptyList()
        return landmarks.flatMap { landmarkList ->
            landmarkList.flatMap { landmark ->
                listOf(
                    landmark.x(),
                    landmark.y(),
                    landmark.z()
                )
            }
        }
    }

    private fun convertFaceLandmarks(landmarks: List<List<NormalizedLandmark>>?): List<Float> {
        if (landmarks == null) return emptyList()
        return landmarks.flatMap { landmarkList ->
            landmarkList
                .take(468) // Force 468 landmarks (Holistic compatibility)
                .map { listOf(it.x(), it.y(), it.z()) }
                .flatten()
        }
    }
    /**
     * Combines the latest available hand, pose, and face keypoints into a single fixed-size feature vector.
     * It then appends this frame’s feature vector to a sequence. Once 30 frames are accumulated,
     * it runs the TFLite model (similar to your Python code).
     */
    private fun processLatestLandmarks() {
        cameraExecutor.execute {
            try {
                // Expected sizes (adjust if your model expects different sizes):
                val expectedHandSize = 2 * 21 * 3    // 126 (matches Python)
                val expectedPoseSize = 33 * 4        // 132 (now includes visibility)
                val expectedFaceSize = 468 * 3       // 1404 (matches Python)
                val expectedTotalSize = expectedHandSize + expectedPoseSize + expectedFaceSize // 1662 total

                val combinedKeypoints = mutableListOf<Float>()

                // Process hand landmarks
                val handFloats = if (lastHandLandmarks != null && lastHandLandmarks!!.isNotEmpty()) {
                    convertLandmarks(lastHandLandmarks)
                } else {
                    emptyList()
                }

                // Add hand landmarks or padding
                combinedKeypoints.addAll(handFloats)
                // If we don't have enough hand data, pad with zeros
                if (handFloats.size < expectedHandSize) {
                    combinedKeypoints.addAll(List(expectedHandSize - handFloats.size) { 0f })
                }

                // Process pose landmarks
                val poseFloats = if (lastPoseLandmarks != null && lastPoseLandmarks!!.isNotEmpty()) {
                    convertPoseLandmarks(lastPoseLandmarks)
                } else {
                    emptyList()
                }

                // Add pose landmarks or padding
                combinedKeypoints.addAll(poseFloats)
                if (poseFloats.size < expectedPoseSize) {
                    combinedKeypoints.addAll(List(expectedPoseSize - poseFloats.size) { 0f })
                }

                // Process face landmarks
                val faceFloats = if (lastFaceLandmarks != null && lastFaceLandmarks!!.isNotEmpty()) {
                    convertFaceLandmarks(lastFaceLandmarks)
                } else {
                    emptyList()
                }

                // Add face landmarks or padding
                combinedKeypoints.addAll(faceFloats)
                if (faceFloats.size < expectedFaceSize) {
                    combinedKeypoints.addAll(List(expectedFaceSize - faceFloats.size) { 0f })
                }

                // Debug log the keypoint sizes
                Log.d(TAG, "Hand: ${handFloats.size}, Pose: ${poseFloats.size}, Face: ${faceFloats.size}")

                if (combinedKeypoints.size != expectedTotalSize) {
                    Log.e(TAG, "Combined keypoints size mismatch: ${combinedKeypoints.size} vs expected $expectedTotalSize")
                    return@execute
                }

                // Append the current frame's keypoints to the sequence (keeping the last 30 frames)
                synchronized(sequence) {
                    sequence.add(combinedKeypoints.toFloatArray())
                    while (sequence.size > 30) {
                        sequence.removeAt(0)
                    }
                }

                // When we have a sequence of 30 frames, run inference
                if (sequence.size == 30) {
                    // Prepare a 3D input: [1, 30, expectedTotalSize]
                    val inputArray = Array(1) { Array(30) { FloatArray(expectedTotalSize) } }
                    synchronized(sequence) {
                        for (i in 0 until 30) {
                            inputArray[0][i] = sequence[i]
                        }
                    }

                    // Create output array with the size of actions
                    val output = Array(1) { FloatArray(actions.size) }

                    // Run inference
                    interpreter.run(inputArray, output)
                    val res = output[0]

                    // Find the index with maximum confidence
                    val predictedActionIndex = res.indices.maxByOrNull { res[it] } ?: -1

                    // Add to predictions list
                    if (predictedActionIndex != -1 && res[predictedActionIndex] > 0.1f) {
                        predictions.add(predictedActionIndex)

                        // Log the prediction
                        Log.d(TAG, "Predicted action: ${actions[predictedActionIndex]}, confidence: ${res[predictedActionIndex]}")



                        // Python logic translated to Kotlin:
                        // if np.unique(predictions[-10:])[0] == np.argmax(res):
                        //     if res[np.argmax(res)] > threshold:
                        // Check if the last 5 predictions are consistent
                        if (predictions.size >= 5) {
                            val last5 = predictions.takeLast(5)
                            val uniquePredictions = last5.toSet()

                            if (uniquePredictions.size == 1 && uniquePredictions.first() == predictedActionIndex) {
                                if (res[predictedActionIndex] > threshold) {
                                    val action = actions[predictedActionIndex]
                                    if (sentence.isEmpty() || sentence.last() != action) {
                                        sentence.add(action)
                                        predictions.clear() // Reset after detection
                                        Log.d(TAG, "Added action to sentence: $action")
                                    }
                                }
                            }
                        }

                        // Limit sentence size to 5
                        while (sentence.size > 5) {
                            sentence.removeAt(0)
                        }

                        // Update the UI with current sentence
                        runOnUiThread {
                            val sentenceText = sentence.joinToString(" ")
                            Log.d(TAG, "Current sentence: $sentenceText")
                            overlayView.updateSentence(sentenceText)
                        }
                    }
                } else {
                    Log.d(TAG, "Building sequence: ${sequence.size}/30 frames")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing landmarks", e)
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarkerHelper.clear()
        poseLandmarkerHelper.clear()
        faceLandmarkerHelper.clear()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
