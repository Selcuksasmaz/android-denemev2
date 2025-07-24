package com.example.zedeneme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.util.Log
import com.example.zedeneme.repository.FaceRepository
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.TensorFlowFaceRecognition
import com.example.zedeneme.data.DetectedFace
import com.example.zedeneme.data.RecognitionResult

data class RecognitionState(
    val isLoading: Boolean = false,
    val recognitionResults: List<RecognitionResult> = emptyList(),
    val detectedFaces: List<DetectedFace> = emptyList(),
    val errorMessage: String? = null,
    val processingStatus: String = "",
    val isRealTimeMode: Boolean = true,
    val confidenceThreshold: Float = 0.75f,
    val tensorFlowEnabled: Boolean = true,
    val recognitionStats: Map<String, Any> = emptyMap(),
    val lastRecognitionTime: Long = 0L,
    val totalRecognitions: Int = 0,
    val successfulRecognitions: Int = 0
)

class FaceRecognitionViewModel(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val tensorFlowEngine: TensorFlowFaceRecognition
) : ViewModel() {

    private val _state = MutableStateFlow(RecognitionState())
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    companion object {
        private const val TAG = "FaceRecognitionVM"
        private const val RECOGNITION_COOLDOWN_MS = 1000L // 1 second between recognitions
    }

    init {
        loadRecognitionStats()
    }

    fun processCameraFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()

                // Cooldown check for real-time mode
                if (_state.value.isRealTimeMode &&
                    currentTime - _state.value.lastRecognitionTime < RECOGNITION_COOLDOWN_MS) {
                    return@launch
                }

                _state.value = _state.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    processingStatus = "🔍 Yüz algılama..."
                )

                // Face detection
                val detectedFaces = faceDetectionEngine.detectFaces(bitmap)
                Log.d(TAG, "🎯 Detected ${detectedFaces.size} faces")

                if (detectedFaces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        detectedFaces = emptyList(),
                        recognitionResults = emptyList(),
                        processingStatus = "👤 Yüz bulunamadı",
                        lastRecognitionTime = currentTime
                    )
                    return@launch
                }

                _state.value = _state.value.copy(
                    detectedFaces = detectedFaces,
                    processingStatus = "🧠 TensorFlow Lite ile tanıma..."
                )

                // Face recognition
                val recognitionResults = mutableListOf<RecognitionResult>()

                for ((index, detectedFace) in detectedFaces.withIndex()) {
                    _state.value = _state.value.copy(
                        processingStatus = "🧠 Yüz tanıma... (${index + 1}/${detectedFaces.size})"
                    )

                    val result = faceRecognitionEngine.recognizeFace(detectedFace)

                    if (result != null && result.confidence >= _state.value.confidenceThreshold) {
                        recognitionResults.add(result)
                        Log.d(TAG, "✅ Recognition: ${result.personName} (${result.confidence})")
                    } else {
                        Log.d(TAG, "❓ Unknown face or low confidence")
                    }
                }

                val totalRecognitions = _state.value.totalRecognitions + detectedFaces.size
                val successfulRecognitions = _state.value.successfulRecognitions + recognitionResults.size

                _state.value = _state.value.copy(
                    isLoading = false,
                    recognitionResults = recognitionResults,
                    processingStatus = if (recognitionResults.isNotEmpty()) {
                        "✅ ${recognitionResults.size} kişi tanındı"
                    } else {
                        "❓ Tanınmayan yüz(ler)"
                    },
                    lastRecognitionTime = currentTime,
                    totalRecognitions = totalRecognitions,
                    successfulRecognitions = successfulRecognitions
                )

                // Update stats
                updateRecognitionStats()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Camera frame processing error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "❌ Tanıma hatası: ${e.message}",
                    processingStatus = "❌ Hata oluştu"
                )
            }
        }
    }

    fun setConfidenceThreshold(threshold: Float) {
        _state.value = _state.value.copy(
            confidenceThreshold = threshold.coerceIn(0f, 1f)
        )
        Log.d(TAG, "🎯 Confidence threshold set to: $threshold")
    }

    fun toggleRealTimeMode() {
        val newMode = !_state.value.isRealTimeMode
        _state.value = _state.value.copy(isRealTimeMode = newMode)
        Log.d(TAG, "🔄 Real-time mode: $newMode")
    }

    fun clearResults() {
        _state.value = _state.value.copy(
            recognitionResults = emptyList(),
            detectedFaces = emptyList(),
            errorMessage = null,
            processingStatus = "",
            totalRecognitions = 0,
            successfulRecognitions = 0
        )
        Log.d(TAG, "🗑️ Recognition results cleared")
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun getDetailedStats(): Map<String, Any> {
        val currentState = _state.value
        val recognitionEngine = faceRecognitionEngine

        return mapOf(
            // Current session stats
            "total_recognitions" to currentState.totalRecognitions,
            "successful_recognitions" to currentState.successfulRecognitions,
            "success_rate" to if (currentState.totalRecognitions > 0) {
                (currentState.successfulRecognitions.toFloat() / currentState.totalRecognitions * 100)
            } else 0f,

            // Current settings
            "confidence_threshold" to currentState.confidenceThreshold,
            "realtime_mode" to currentState.isRealTimeMode,
            "tensorflow_enabled" to currentState.tensorFlowEnabled,

            // Engine stats
            "recognition_engine_stats" to recognitionEngine.getRecognitionStats(),

            // Current detection
            "detected_faces_count" to currentState.detectedFaces.size,
            "recognized_faces_count" to currentState.recognitionResults.size,
            "last_recognition_time" to currentState.lastRecognitionTime,

            // Performance
            "processing_status" to currentState.processingStatus,
            "is_loading" to currentState.isLoading
        )
    }

    private fun loadRecognitionStats() {
        viewModelScope.launch {
            try {
                val stats = faceRecognitionEngine.getRecognitionStats()
                _state.value = _state.value.copy(recognitionStats = stats)
                Log.d(TAG, "📊 Recognition stats loaded: $stats")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load recognition stats", e)
            }
        }
    }

    private fun updateRecognitionStats() {
        viewModelScope.launch {
            try {
                val detailedStats = getDetailedStats()
                _state.value = _state.value.copy(recognitionStats = detailedStats)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to update recognition stats", e)
            }
        }
    }

    // Manual recognition trigger (for non-realtime mode)
    fun triggerManualRecognition(bitmap: Bitmap) {
        if (!_state.value.isRealTimeMode) {
            processCameraFrame(bitmap)
        }
    }

    // Get recognition history
    suspend fun getRecognitionHistory(): List<RecognitionResult> {
        return try {
            // This could be extended to store recognition history in database
            _state.value.recognitionResults
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get recognition history", e)
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🔒 ViewModel cleared")
    }
}

// Factory class for ViewModel creation
class FaceRecognitionViewModelFactory(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val tensorFlowEngine: TensorFlowFaceRecognition
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRecognitionViewModel::class.java)) {
            return FaceRecognitionViewModel(
                repository = repository,
                faceDetectionEngine = faceDetectionEngine,
                faceRecognitionEngine = faceRecognitionEngine,
                tensorFlowEngine = tensorFlowEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}