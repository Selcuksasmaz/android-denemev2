package com.example.zedeneme.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.zedeneme.data.RecognitionState
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.engine.EnhancedFaceRecognitionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaceRecognitionViewModel(
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine,
    private val enhancedFaceRecognitionEngine: EnhancedFaceRecognitionEngine? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecognitionState())
    val uiState: StateFlow<RecognitionState> = _uiState.asStateFlow()

    fun startRecognition() {
        _uiState.value = _uiState.value.copy(
            isRecognizing = true,
            error = null
        )
    }

    fun stopRecognition() {
        _uiState.value = _uiState.value.copy(
            isRecognizing = false,
            isProcessing = false
        )
    }

    fun processFrame(bitmap: Bitmap) {
        val currentState = _uiState.value
        if (!currentState.isRecognizing || currentState.isProcessing) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isProcessing = true)

            try {
                val detectedFaces = faceDetectionEngine.detectFaces(bitmap)
                val bestFace = detectedFaces.maxByOrNull { it.confidence }

                if (bestFace != null && faceDetectionEngine.isQualityGoodEnough(bestFace)) {
                    _uiState.value = _uiState.value.copy(detectedFace = bestFace)

                    // Use enhanced engine if available, otherwise fall back to original
                    val recognitionResult = if (enhancedFaceRecognitionEngine != null) {
                        // Enhanced recognition (TensorFlow Lite + fallback)
                        enhancedFaceRecognitionEngine.recognizeFace(
                            bestFace.bitmap,
                            bestFace.landmarks,
                            bestFace.angle
                        )
                    } else {
                        // Original recognition pipeline
                        val features = featureExtractionEngine.extractCombinedFeatures(
                            bestFace.bitmap,
                            bestFace.landmarks
                        )

                        if (faceRecognitionEngine.isFeatureQualityGood(features)) {
                            faceRecognitionEngine.recognizeFace(features, bestFace.angle)
                        } else {
                            null
                        }
                    }

                    if (recognitionResult != null) {
                        _uiState.value = _uiState.value.copy(
                            lastResult = recognitionResult,
                            isProcessing = false,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = "Düşük kaliteli görüntü veya tanıma başarısız"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        detectedFace = null,
                        isProcessing = false
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Tanıma hatası: ${e.message}"
                )
            }
        }
    }

    fun clearLastResult() {
        _uiState.value = _uiState.value.copy(
            lastResult = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Get the current engine status for debugging/monitoring
     */
    fun getEngineStatus(): String {
        return enhancedFaceRecognitionEngine?.getEngineStatus()?.let { status ->
            """
            Engine: ${status.activEngine}
            TensorFlow Available: ${status.isTensorFlowLiteAvailable}
            Feature Size: ${status.featureSize}
            Threshold: ${status.recognitionThreshold}
            Expected Accuracy: ${status.expectedAccuracy}
            """.trimIndent()
        } ?: "Using Original Engine Only"
    }
    
    /**
     * Toggle between TensorFlow Lite and original engine
     */
    fun toggleTensorFlowLite(enabled: Boolean) {
        enhancedFaceRecognitionEngine?.setUseTensorFlowLite(enabled)
    }
    
    /**
     * Benchmark the current system performance
     */
    fun runPerformanceBenchmark(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val detectedFace = faceDetectionEngine.detectSingleFace(bitmap)
                if (detectedFace != null && enhancedFaceRecognitionEngine != null) {
                    val metrics = enhancedFaceRecognitionEngine.benchmarkPerformance(
                        detectedFace.bitmap,
                        detectedFace.landmarks
                    )
                    
                    val benchmarkInfo = """
                        Performance Benchmark:
                        Feature Extraction: ${metrics.featureExtractionTime}ms
                        Recognition: ${metrics.recognitionTime}ms
                        Total: ${metrics.totalTime}ms
                        Engine: ${metrics.engineUsed}
                        Confidence: ${metrics.confidence}
                    """.trimIndent()
                    
                    android.util.Log.d("FaceRecognitionVM", benchmarkInfo)
                }
            } catch (e: Exception) {
                android.util.Log.e("FaceRecognitionVM", "Benchmark failed", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        enhancedFaceRecognitionEngine?.close()
    }
}

class FaceRecognitionViewModelFactory(
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine,
    private val enhancedFaceRecognitionEngine: EnhancedFaceRecognitionEngine? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FaceRecognitionViewModel(
                faceDetectionEngine, 
                faceRecognitionEngine, 
                featureExtractionEngine,
                enhancedFaceRecognitionEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}