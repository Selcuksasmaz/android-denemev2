package com.example.zedeneme.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.zedeneme.data.FaceFeatures
import com.example.zedeneme.data.RecognitionResult
import com.example.zedeneme.data.FaceAngle
import com.example.zedeneme.data.FaceLandmarks
import com.example.zedeneme.repository.FaceRepository
import com.google.gson.Gson
import kotlin.math.*

/**
 * Enhanced Face Recognition Engine with TensorFlow Lite FaceNet support
 * Falls back to original algorithm if TF Lite is not available
 */
class EnhancedFaceRecognitionEngine(
    private val context: Context,
    private val repository: FaceRepository,
    private val featureExtractor: FeatureExtractionEngine
) {

    private val gson = Gson()
    private var tensorFlowEngine: TensorFlowFaceRecognition? = null
    private val originalEngine: FaceRecognitionEngine
    
    // Configuration
    private var useTensorFlowLite = true
    private var fallbackToOriginal = true
    
    companion object {
        private const val TAG = "EnhancedFaceRecognition"
        private const val TENSORFLOW_THRESHOLD = 0.75f
        private const val ORIGINAL_THRESHOLD = 0.65f
    }
    
    init {
        // Initialize original engine as fallback
        originalEngine = FaceRecognitionEngine(repository, featureExtractor)
        
        // Try to initialize TensorFlow Lite engine
        initializeTensorFlowEngine()
    }
    
    private fun initializeTensorFlowEngine() {
        try {
            tensorFlowEngine = TensorFlowFaceRecognition(context, repository)
            if (tensorFlowEngine?.isModelLoaded() == true) {
                android.util.Log.d(TAG, "TensorFlow Lite engine initialized successfully")
            } else {
                android.util.Log.w(TAG, "TensorFlow Lite model not available, using fallback")
                tensorFlowEngine = null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize TensorFlow Lite engine", e)
            tensorFlowEngine = null
        }
    }
    
    /**
     * Extract features using the best available method
     */
    suspend fun extractFeatures(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray {
        return if (useTensorFlowLite && tensorFlowEngine?.isModelLoaded() == true) {
            // Use TensorFlow Lite FaceNet for feature extraction
            tensorFlowEngine?.extractFaceEmbedding(bitmap) ?: run {
                android.util.Log.w(TAG, "TF Lite extraction failed, falling back to original")
                featureExtractor.extractCombinedFeatures(bitmap, landmarks)
            }
        } else {
            // Use original feature extraction
            featureExtractor.extractCombinedFeatures(bitmap, landmarks)
        }
    }
    
    /**
     * Recognize face using the best available method
     */
    suspend fun recognizeFace(
        bitmap: Bitmap,
        landmarks: FaceLandmarks,
        currentAngle: FaceAngle
    ): RecognitionResult? {
        
        return if (useTensorFlowLite && tensorFlowEngine?.isModelLoaded() == true) {
            // Try TensorFlow Lite recognition first
            try {
                val result = tensorFlowEngine?.recognizeFace(bitmap, currentAngle)
                if (result != null) {
                    android.util.Log.d(TAG, "TF Lite recognition successful: ${result.confidence}")
                    return result
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "TF Lite recognition failed", e)
            }
            
            // Fallback to original if enabled
            if (fallbackToOriginal) {
                android.util.Log.d(TAG, "Falling back to original recognition")
                recognizeWithOriginalEngine(bitmap, landmarks, currentAngle)
            } else {
                null
            }
        } else {
            // Use original recognition engine
            recognizeWithOriginalEngine(bitmap, landmarks, currentAngle)
        }
    }
    
    private suspend fun recognizeWithOriginalEngine(
        bitmap: Bitmap,
        landmarks: FaceLandmarks,
        currentAngle: FaceAngle
    ): RecognitionResult? {
        val features = featureExtractor.extractCombinedFeatures(bitmap, landmarks)
        return originalEngine.recognizeFace(features, currentAngle)
    }
    
    /**
     * Check feature quality using the active engine
     */
    fun isFeatureQualityGood(features: FloatArray, bitmap: Bitmap? = null): Boolean {
        return if (useTensorFlowLite && tensorFlowEngine?.isModelLoaded() == true) {
            // Use TensorFlow Lite quality check
            tensorFlowEngine?.isEmbeddingQualityGood(features) ?: false
        } else {
            // Use original quality check
            originalEngine.isFeatureQualityGood(features)
        }
    }
    
    /**
     * Get the current feature vector size
     */
    fun getFeatureSize(): Int {
        return if (useTensorFlowLite && tensorFlowEngine?.isModelLoaded() == true) {
            512 // TensorFlow Lite FaceNet embedding size
        } else {
            420 // Original combined features size (256 + 144 + 20)
        }
    }
    
    /**
     * Get current recognition threshold
     */
    fun getRecognitionThreshold(): Float {
        return if (useTensorFlowLite && tensorFlowEngine?.isModelLoaded() == true) {
            TENSORFLOW_THRESHOLD
        } else {
            ORIGINAL_THRESHOLD
        }
    }
    
    /**
     * Get engine status and performance info
     */
    fun getEngineStatus(): EngineStatus {
        val isTensorFlowAvailable = tensorFlowEngine?.isModelLoaded() == true
        val activeEngine = if (useTensorFlowLite && isTensorFlowAvailable) {
            "TensorFlow Lite FaceNet"
        } else {
            "Original (LBP + HOG + Geometric)"
        }
        
        return EngineStatus(
            activEngine = activeEngine,
            isTensorFlowLiteAvailable = isTensorFlowAvailable,
            featureSize = getFeatureSize(),
            recognitionThreshold = getRecognitionThreshold(),
            expectedAccuracy = if (isTensorFlowAvailable && useTensorFlowLite) "90-95%" else "70-80%"
        )
    }
    
    /**
     * Enable or disable TensorFlow Lite usage
     */
    fun setUseTensorFlowLite(enabled: Boolean) {
        useTensorFlowLite = enabled
        android.util.Log.d(TAG, "TensorFlow Lite usage ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Enable or disable fallback to original engine
     */
    fun setFallbackToOriginal(enabled: Boolean) {
        fallbackToOriginal = enabled
        android.util.Log.d(TAG, "Fallback to original ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Batch recognition for multiple faces
     */
    suspend fun recognizeMultipleFaces(
        faceData: List<Triple<Bitmap, FaceLandmarks, FaceAngle>>
    ): List<RecognitionResult?> {
        return faceData.map { (bitmap, landmarks, angle) ->
            recognizeFace(bitmap, landmarks, angle)
        }
    }
    
    /**
     * Performance metrics for monitoring
     */
    suspend fun benchmarkPerformance(bitmap: Bitmap, landmarks: FaceLandmarks): PerformanceMetrics {
        val startTime = System.currentTimeMillis()
        
        val features = extractFeatures(bitmap, landmarks)
        val extractionTime = System.currentTimeMillis() - startTime
        
        val recognitionStart = System.currentTimeMillis()
        val result = recognizeFace(bitmap, landmarks, FaceAngle(0f, 0f, 0f))
        val recognitionTime = System.currentTimeMillis() - recognitionStart
        
        return PerformanceMetrics(
            featureExtractionTime = extractionTime,
            recognitionTime = recognitionTime,
            totalTime = extractionTime + recognitionTime,
            featureSize = features.size,
            engineUsed = getEngineStatus().activEngine,
            confidence = result?.confidence ?: 0f
        )
    }
    
    fun close() {
        tensorFlowEngine?.close()
    }
}

/**
 * Data class for engine status information
 */
data class EngineStatus(
    val activEngine: String,
    val isTensorFlowLiteAvailable: Boolean,
    val featureSize: Int,
    val recognitionThreshold: Float,
    val expectedAccuracy: String
)

/**
 * Data class for performance metrics
 */
data class PerformanceMetrics(
    val featureExtractionTime: Long,
    val recognitionTime: Long,
    val totalTime: Long,
    val featureSize: Int,
    val engineUsed: String,
    val confidence: Float
)