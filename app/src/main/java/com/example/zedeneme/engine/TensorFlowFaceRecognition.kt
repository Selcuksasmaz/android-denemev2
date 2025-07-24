package com.example.zedeneme.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.example.zedeneme.data.FaceAngle
import com.example.zedeneme.data.RecognitionResult
import com.example.zedeneme.repository.FaceRepository
import com.google.gson.Gson
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

class TensorFlowFaceRecognition(
    private val context: Context,
    private val repository: FaceRepository
) {
    
    private var interpreter: Interpreter? = null
    private val gson = Gson()
    
    companion object {
        private const val TAG = "TensorFlowFaceRecognition"
        private const val MODEL_FILE = "facenet_mobile.tflite"
        private const val INPUT_SIZE = 160
        private const val EMBEDDING_SIZE = 512
        private const val RECOGNITION_THRESHOLD = 0.75f // Higher threshold for better accuracy
        private const val PIXEL_SIZE = 3 // RGB
    }
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            // For now, create a placeholder model buffer since we don't have the actual model file yet
            // In a real implementation, this would load the FaceNet model from assets
            interpreter = createPlaceholderInterpreter()
            if (interpreter != null) {
                Log.d(TAG, "TensorFlow Lite model loaded successfully")
            } else {
                Log.d(TAG, "TensorFlow Lite model not available, will use fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model", e)
            interpreter = null
        }
    }
    
    private fun createPlaceholderInterpreter(): Interpreter {
        // This is a placeholder implementation for demonstration purposes
        // In a real scenario, you would load the actual FaceNet model from assets
        
        // For now, we'll return null to indicate model is not available
        // The enhanced engine will fall back to the original algorithm
        return null
    }
    
    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Extract 512-dimensional face embedding using FaceNet model
     */
    fun extractFaceEmbedding(faceBitmap: Bitmap): FloatArray? {
        return try {
            val preprocessedBitmap = preprocessImage(faceBitmap)
            val inputBuffer = bitmapToByteBuffer(preprocessedBitmap)
            
            // Create output buffer for 512D embedding
            val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
            
            // Run inference (placeholder - would use actual model)
            // interpreter?.run(inputBuffer, outputBuffer)
            
            // For demonstration, create a normalized random embedding
            val embedding = generatePlaceholderEmbedding()
            
            // L2 normalize the embedding
            l2Normalize(embedding)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face embedding", e)
            null
        }
    }
    
    private fun generatePlaceholderEmbedding(): FloatArray {
        // This generates a placeholder embedding for demonstration
        // In real implementation, this would come from the TensorFlow Lite model
        val embedding = FloatArray(EMBEDDING_SIZE)
        val random = java.util.Random(System.currentTimeMillis())
        
        for (i in embedding.indices) {
            embedding[i] = random.nextGaussian().toFloat()
        }
        
        return embedding
    }
    
    /**
     * Preprocess image for FaceNet model: resize to 160x160 and normalize to [-1, 1]
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Resize to 160x160
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // Ensure RGB format
        val rgbBitmap = if (resized.config != Bitmap.Config.ARGB_8888) {
            resized.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            resized
        }
        
        return rgbBitmap
    }
    
    /**
     * Convert bitmap to ByteBuffer for TensorFlow Lite input
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                
                // Extract RGB values and normalize to [-1, 1]
                val r = ((value shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((value shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (value and 0xFF) / 127.5f - 1.0f
                
                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }
        
        return byteBuffer
    }
    
    /**
     * L2 normalize the embedding vector
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / norm
            }
        }
        return embedding
    }
    
    /**
     * Calculate cosine similarity between two L2-normalized embeddings
     */
    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        
        // For L2-normalized vectors, cosine similarity = dot product
        return embedding1.zip(embedding2) { a, b -> a * b }.sum()
    }
    
    /**
     * Recognize face using TensorFlow Lite FaceNet embeddings
     */
    suspend fun recognizeFace(
        faceBitmap: Bitmap,
        currentAngle: FaceAngle
    ): RecognitionResult? {
        
        val embedding = extractFaceEmbedding(faceBitmap) ?: return null
        
        val allProfileFeatures = repository.getAllFeaturesForRecognition()
        if (allProfileFeatures.isEmpty()) return null
        
        val angleType = currentAngle.getAngleType()
        var bestMatch: Pair<String, Float>? = null
        var bestConfidence = 0f
        var bestMatchedAngle = angleType
        
        allProfileFeatures.forEach { (profileId, profileFeatures) ->
            profileFeatures.forEach { storedFeature ->
                try {
                    val storedEmbedding = gson.fromJson(storedFeature.features, FloatArray::class.java)
                    
                    // Ensure stored embedding is also 512D (migration compatibility)
                    if (storedEmbedding.size == EMBEDDING_SIZE) {
                        val similarity = calculateCosineSimilarity(embedding, storedEmbedding)
                        
                        // Apply angle-based boost/penalty
                        val adjustedSimilarity = if (storedFeature.angle == angleType) {
                            similarity + 0.05f // Small boost for same angle
                        } else {
                            similarity - 0.02f // Small penalty for different angle
                        }
                        
                        if (adjustedSimilarity > bestConfidence) {
                            bestConfidence = adjustedSimilarity
                            bestMatch = Pair(profileId, adjustedSimilarity)
                            bestMatchedAngle = storedFeature.angle
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing stored embedding", e)
                }
            }
        }
        
        return if (bestMatch != null && bestConfidence > RECOGNITION_THRESHOLD) {
            val profile = repository.getProfile(bestMatch.first)
            profile?.let {
                RecognitionResult(
                    personId = it.id,
                    personName = it.personName,
                    confidence = bestConfidence.coerceIn(0f, 1f),
                    matchedAngle = bestMatchedAngle,
                    timestamp = System.currentTimeMillis()
                )
            }
        } else {
            null
        }
    }
    
    /**
     * Check if the extracted embedding has good quality
     */
    fun isEmbeddingQualityGood(embedding: FloatArray): Boolean {
        // Check for NaN or infinite values
        if (embedding.any { it.isNaN() || it.isInfinite() }) return false
        
        // Check if embedding is properly normalized (L2 norm should be ~1.0)
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        return norm > 0.9f && norm < 1.1f
    }
    
    /**
     * Get feature vector size (512 for FaceNet)
     */
    fun getFeatureSize(): Int = EMBEDDING_SIZE
    
    /**
     * Check if TensorFlow Lite model is loaded and ready
     */
    fun isModelLoaded(): Boolean = interpreter != null
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}