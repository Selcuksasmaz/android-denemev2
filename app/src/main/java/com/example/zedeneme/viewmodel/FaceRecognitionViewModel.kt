package com.example.zedeneme.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.zedeneme.data.RecognitionState
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaceRecognitionViewModel(
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine
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

                    // Feature extraction
                    val features = featureExtractionEngine.extractCombinedFeatures(
                        bestFace.bitmap,
                        bestFace.landmarks
                    )

                    // Feature quality check
                    if (faceRecognitionEngine.isFeatureQualityGood(features)) {
                        // Recognition
                        val recognitionResult = faceRecognitionEngine.recognizeFace(
                            features,
                            bestFace.angle
                        )

                        _uiState.value = _uiState.value.copy(
                            lastResult = recognitionResult,
                            isProcessing = false,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            error = "Düşük kaliteli görüntü"
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
}

class FaceRecognitionViewModelFactory(
    private val faceDetectionEngine: FaceDetectionEngine,
    private val faceRecognitionEngine: FaceRecognitionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FaceRecognitionViewModel(faceDetectionEngine, faceRecognitionEngine, featureExtractionEngine) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}