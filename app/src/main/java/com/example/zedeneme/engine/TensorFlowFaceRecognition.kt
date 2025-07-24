package com.example.zedeneme.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.*

class TensorFlowFaceRecognition(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE = 160
    private val OUTPUT_SIZE = 512

    companion object {
        private const val TAG = "TFLiteFaceRecognition"
        private const val MODEL_FILENAME = "facenet_mobile.tflite"
    }

    init {
        try {
            interpreter = Interpreter(loadModelFile())
            Log.d(TAG, "✅ TensorFlow Lite model yüklendi")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model yükleme hatası", e)
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILENAME)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun extractFeatures(bitmap: Bitmap): FloatArray? {
        return try {
            val startTime = System.currentTimeMillis()

            // 1. Resize bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 2. Preprocess
            val input = preprocessImage(resizedBitmap)

            // 3. Run inference
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            interpreter?.run(input, output)

            // 4. Normalize
            val features = output[0]
            val normalizedFeatures = l2Normalize(features)

            val time = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Features extracted: ${normalizedFeatures.size}D in ${time}ms")

            normalizedFeatures
        } catch (e: Exception) {
            Log.e(TAG, "❌ Feature extraction error", e)
            null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val pixelValue = intValues[pixel++]

                // RGB'yi [-1, 1] aralığına normalize et
                val r = ((pixelValue shr 16 and 0xFF) - 127.5f) / 127.5f
                val g = ((pixelValue shr 8 and 0xFF) - 127.5f) / 127.5f
                val b = ((pixelValue and 0xFF) - 127.5f) / 127.5f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }

        return byteBuffer
    }

    private fun l2Normalize(features: FloatArray): FloatArray {
        val norm = sqrt(features.sumOf { (it * it).toDouble() }).toFloat()

        return if (norm > 0) {
            features.map { it / norm }.toFloatArray()
        } else {
            features
        }
    }

    fun calculateSimilarity(features1: FloatArray, features2: FloatArray): Float {
        if (features1.size != features2.size) return 0f

        // Cosine similarity
        val cosineSimilarity = features1.zip(features2) { a, b -> a * b }.sum()

        // [0,1] aralığına çevir
        return (cosineSimilarity + 1f) / 2f
    }

    fun isFeatureQualityGood(features: FloatArray?): Boolean {
        if (features == null) return false

        // NaN/Infinite kontrolü
        if (features.any { it.isNaN() || it.isInfinite() }) return false

        // Magnitude kontrolü
        val magnitude = sqrt(features.sumOf { (it * it).toDouble() }).toFloat()
        if (magnitude < 0.8f || magnitude > 1.2f) return false

        return true
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "🔒 TensorFlow model released")
    }
}