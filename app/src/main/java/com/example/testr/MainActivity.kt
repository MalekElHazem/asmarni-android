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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), HandLandmarkerHelper.HandLandmarkerListener, PoseLandmarkerHelper.PoseLandmarkerListener {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yuvConverter: YuvToRgbConverter

    // Camera configuration
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var cameraProvider: ProcessCameraProvider? = null

    @androidx.camera.core.ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yuvConverter = YuvToRgbConverter(this)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.camera_view)
        overlayView = findViewById(R.id.overlay_view)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Initialize Hand Landmarker Helper
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            listener = this
        )
        poseLandmarkerHelper = PoseLandmarkerHelper(this, this)

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy)
                            val rotatedBitmap = BitmapUtils.rotateBitmap(
                                bitmap,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                            val timestamp = SystemClock.uptimeMillis()

                            // Pass the frame to both detectors.
                            handLandmarkerHelper.detectLiveStream(mpImage, timestamp)
                            poseLandmarkerHelper.detectLiveStream(mpImage, timestamp)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image", e)
                        }
                    }
                }

            // Select camera lens
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Hand landmark results callback.
    override fun onResults(result: HandLandmarkerResult, input: MPImage) {
        runOnUiThread {
            // You may create a dedicated method in your overlay to update hand results.
            overlayView.setHandResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
        }
    }

    // Pose landmark results callback.
    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            // Create or update a method in your overlay for drawing pose results.
            overlayView.setPoseResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
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
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}