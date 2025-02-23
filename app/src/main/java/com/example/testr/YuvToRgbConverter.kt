package com.example.testr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

class YuvToRgbConverter(private val context: Context) {

    fun yuvToRgb(image: Image, output: Bitmap) {
        // Convert the YUV image to NV21 byte array
        val nv21 = yuv420ToNv21(image)
        // Convert NV21 byte array to YuvImage
        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        // Compress YuvImage to JPEG
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        // Decode JPEG byte array to Bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        // You might copy the bitmap to your output, or use it directly.
        // (Ensure the output bitmap is mutable or adjust accordingly.)
        bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(ByteArray(bitmap.byteCount)))
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        // TODO: Implement conversion from YUV_420_888 to NV21.
        // This usually involves extracting the Y, U, and V planes and interleaving U and V.
        throw NotImplementedError("YUV_420_888 to NV21 conversion is not implemented")
    }
}
