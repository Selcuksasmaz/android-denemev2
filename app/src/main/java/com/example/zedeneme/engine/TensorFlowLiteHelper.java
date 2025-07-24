package com.example.zedeneme.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TensorFlowLiteHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        private const val TAG = "TFLiteHelper"
        private const val MODEL_PATH = "face_feature_model.tflite"
        private const val INPUT_SIZE = 160 // FaceNet input size
        private const val FEATURE_SIZE = 512 // FaceNet output feature size
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()

            // GPU delegate kullanmayı dene
            val compatList = CompatibilityList()
            val options = Interpreter.Options()

            if (compatList.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU delegate kullanıldı")
            } else {
                // CPU'da çalıştır
                options.setNumThreads(4)
                Log.d(TAG, "CPU'da çalışıyor")
            }

            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "Model başarıyla yüklendi")

        } catch (e: Exception) {
            Log.e(TAG, "Model yükleme hatası", e)
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun extractFeatures(bitmap: Bitmap): FloatArray? {
        return try {
            val interpreter = this.interpreter ?: return null

            // Bitmap'i model input formatına dönüştür
            val inputBuffer = preprocessBitmap(bitmap)

            // Output buffer hazırla
            val outputBuffer = Array(1) { FloatArray(FEATURE_SIZE) }

            // Model çalıştır
            interpreter.run(inputBuffer, outputBuffer)

            Log.d(TAG, "Feature extraction başarılı, özellik sayısı: ${outputBuffer[0].size}")

            outputBuffer[0]

        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction hatası", e)
            null
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        // Bitmap'i model input size'a resize et
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        // Normalize pixel values to [-1, 1] (FaceNet preprocessing)
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val pixelValue = intValues[pixel++]

                // RGB değerlerini çıkar ve normalize et
                val r = ((pixelValue shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((pixelValue shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (pixelValue and 0xFF) / 127.5f - 1.0f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }

        return byteBuffer
    }

    fun isModelLoaded(): Boolean {
        return interpreter != null
    }

    fun release() {
        try {
            interpreter?.close()
            gpuDelegate?.close()
            Log.d(TAG, "TensorFlow Lite resources temizlendi")
        } catch (e: Exception) {
            Log.e(TAG, "Resource temizleme hatası", e)
        }
    }
}