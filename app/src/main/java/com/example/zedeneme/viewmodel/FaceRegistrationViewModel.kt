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
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.engine.TensorFlowFaceRecognition
import com.example.zedeneme.data.DetectedFace
import com.example.zedeneme.data.FaceProfile
import com.example.zedeneme.data.FaceFeatures
import com.example.zedeneme.data.FaceAngle

data class RegistrationState(
    val isLoading: Boolean = false,
    val capturedFaces: List<DetectedFace> = emptyList(),
    val currentPersonName: String = "",
    val registrationProgress: Float = 0f,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val featureExtractionProgress: String = "",
    val requiredAngles: List<String> = listOf("frontal", "left", "right", "up", "down"),
    val completedAngles: Set<String> = emptySet(),
    val isRegistrationComplete: Boolean = false,
    val tensorFlowEnabled: Boolean = true,
    val currentFeatureType: String = "Unknown"
)

class FaceRegistrationViewModel(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine,
    private val tensorFlowEngine: TensorFlowFaceRecognition
) : ViewModel() {

    private val _state = MutableStateFlow(RegistrationState())
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    companion object {
        private const val TAG = "FaceRegistrationVM"
        private const val MIN_FACES_PER_PERSON = 5
        private const val MAX_FACES_PER_PERSON = 15
    }

    fun setPersonName(name: String) {
        _state.value = _state.value.copy(currentPersonName = name.trim())
        Log.d(TAG, "👤 Person name set: ${name.trim()}")
    }

    fun processCameraFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    featureExtractionProgress = "🔍 Yüz algılama..."
                )

                // Face detection
                val detectedFaces = faceDetectionEngine.detectFaces(bitmap)
                Log.d(TAG, "🎯 Detected ${detectedFaces.size} faces")

                if (detectedFaces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "❌ Yüz algılanamadı. Kamerayı yüzünüze doğru çevirin.",
                        featureExtractionProgress = ""
                    )
                    return@launch
                }

                if (detectedFaces.size > 1) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "⚠️ Birden fazla yüz algılandı. Tek kişi olduğunuzdan emin olun.",
                        featureExtractionProgress = ""
                    )
                    return@launch
                }

                val detectedFace = detectedFaces.first()

                // Feature extraction with TensorFlow Lite
                _state.value = _state.value.copy(
                    featureExtractionProgress = "🧠 TensorFlow Lite ile özellikler çıkarılıyor..."
                )

                val features = featureExtractionEngine.extractCombinedFeatures(
                    detectedFace.bitmap,
                    detectedFace.landmarks
                )

                val featureType = featureExtractionEngine.getFeatureType(features)
                val featureQuality = featureExtractionEngine.assessFeatureQuality(features)

                Log.d(TAG, "✅ Features extracted: ${featureType}, Quality: $featureQuality")

                // Quality check
                if (featureQuality < 0.5f) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "⚠️ Yüz kalitesi düşük. Daha iyi ışıklandırma ve net görüntü deneyin.",
                        featureExtractionProgress = ""
                    )
                    return@launch
                }

                // Determine face angle
                val faceAngle = detectedFace.angle
                Log.d(TAG, "📐 Face angle determined: $faceAngle")

                // Add to captured faces
                val currentFaces = _state.value.capturedFaces.toMutableList()

                // Check if we already have this angle
                val angleCount = currentFaces.count { it.angle.getAngleType() == faceAngle.getAngleType() }
                if (angleCount >= 3) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "✅ Bu açıdan yeterli görüntü var ($faceAngle). Farklı açı deneyin.",
                        featureExtractionProgress = ""
                    )
                    return@launch
                }

                // Add the face with extracted features
                val faceWithFeatures = detectedFace.copy(
                    angle = faceAngle,
                    extractedFeatures = features,
                    confidence = featureQuality
                )
                currentFaces.add(faceWithFeatures)

                // Update completed angles
                val completedAngles = currentFaces.map { it.angle.getAngleType() }.toSet()
                val progress = currentFaces.size.toFloat() / MIN_FACES_PER_PERSON
                val isComplete = currentFaces.size >= MIN_FACES_PER_PERSON &&
                        completedAngles.size >= 3

                _state.value = _state.value.copy(
                    isLoading = false,
                    capturedFaces = currentFaces,
                    completedAngles = completedAngles,
                    registrationProgress = progress.coerceAtMost(1f),
                    isRegistrationComplete = isComplete,
                    successMessage = "✅ Yüz kaydedildi (${currentFaces.size}/${MIN_FACES_PER_PERSON}) - $faceAngle",
                    errorMessage = null,
                    featureExtractionProgress = "",
                    currentFeatureType = featureType
                )

                if (isComplete) {
                    _state.value = _state.value.copy(
                        successMessage = "🎉 Kayıt için yeterli yüz toplanıldı! Kaydet butonuna basın."
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Camera frame processing error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "❌ İşlem hatası: ${e.message}",
                    featureExtractionProgress = ""
                )
            }
        }
    }

    fun saveFaceProfile() {
        viewModelScope.launch {
            try {
                val currentState = _state.value

                if (currentState.currentPersonName.isBlank()) {
                    _state.value = currentState.copy(
                        errorMessage = "⚠️ Lütfen kişi adını girin."
                    )
                    return@launch
                }

                if (currentState.capturedFaces.size < MIN_FACES_PER_PERSON) {
                    _state.value = currentState.copy(
                        errorMessage = "⚠️ En az $MIN_FACES_PER_PERSON yüz görüntüsü gerekli."
                    )
                    return@launch
                }

                _state.value = currentState.copy(
                    isLoading = true,
                    featureExtractionProgress = "💾 Profil kaydediliyor..."
                )

                val profileId = java.util.UUID.randomUUID().toString()
                val faceProfile = FaceProfile(
                    id = profileId,
                    personName = currentState.currentPersonName,
                    frontalFeatures = currentState.capturedFaces.firstOrNull { it.angle.getAngleType() == "frontal" }?.extractedFeatures?.joinToString(",") ?: "",
                    leftProfileFeatures = currentState.capturedFaces.firstOrNull { it.angle.getAngleType() == "left_profile" }?.extractedFeatures?.joinToString(",") ?: "",
                    rightProfileFeatures = currentState.capturedFaces.firstOrNull { it.angle.getAngleType() == "right_profile" }?.extractedFeatures?.joinToString(",") ?: "",
                    upAngleFeatures = currentState.capturedFaces.firstOrNull { it.angle.getAngleType() == "up_angle" }?.extractedFeatures?.joinToString(",") ?: "",
                    downAngleFeatures = currentState.capturedFaces.firstOrNull { it.angle.getAngleType() == "down_angle" }?.extractedFeatures?.joinToString(",") ?: "",
                    registrationDate = System.currentTimeMillis(),
                    isComplete = currentState.isRegistrationComplete
                )

                repository.insertFullProfile(faceProfile)
                Log.d(TAG, "✅ Full profile saved with ID: ${faceProfile.id}")

                var savedCount = 0
                for (face in currentState.capturedFaces) {
                    val storedFeatures = FaceFeatures(
                        profileId = faceProfile.id,
                        features = face.extractedFeatures?.joinToString(",") ?: "",
                        angle = face.angle.getAngleType(),
                        landmarks = "", // Placeholder, you might want to serialize landmarks here
                        confidence = face.confidence,
                        timestamp = System.currentTimeMillis()
                    )

                    repository.saveFeatures(faceProfile.id, face.angle.getAngleType(), face.extractedFeatures ?: floatArrayOf(), storedFeatures.landmarks)
                    savedCount++

                    _state.value = currentState.copy(
                        featureExtractionProgress = "💾 Yüz kaydediliyor... ($savedCount/${currentState.capturedFaces.size})"
                    )
                }

                Log.d(TAG, "✅ All faces saved successfully: $savedCount faces")

                _state.value = RegistrationState(
                    successMessage = "🎉 ${currentState.currentPersonName} başarıyla kaydedildi! ($savedCount yüz)",
                    currentFeatureType = currentState.currentFeatureType
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Face profile save error", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "❌ Kayıt hatası: ${e.message}",
                    featureExtractionProgress = ""
                )
            }
        }
    }

    fun clearCapturedFaces() {
        _state.value = _state.value.copy(
            capturedFaces = emptyList(),
            completedAngles = emptySet(),
            registrationProgress = 0f,
            isRegistrationComplete = false,
            successMessage = null,
            errorMessage = null
        )
        Log.d(TAG, "🗑️ Captured faces cleared")
    }

    fun clearMessages() {
        _state.value = _state.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    fun getTensorFlowStats(): Map<String, Any> {
        return try {
            mapOf(
                "tensorflow_enabled" to _state.value.tensorFlowEnabled,
                "current_feature_type" to _state.value.currentFeatureType,
                "captured_faces" to _state.value.capturedFaces.size,
                "completed_angles" to _state.value.completedAngles.size,
                "registration_progress" to _state.value.registrationProgress,
                "min_faces_required" to MIN_FACES_PER_PERSON,
                "max_faces_allowed" to MAX_FACES_PER_PERSON
            )
        } catch (e: Exception) {
            mapOf("error" to e.message.orEmpty())
        }
    }

    

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🔒 ViewModel cleared")
    }
}

// Factory class for ViewModel creation
class FaceRegistrationViewModelFactory(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine,
    private val tensorFlowEngine: TensorFlowFaceRecognition
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRegistrationViewModel::class.java)) {
            return FaceRegistrationViewModel(
                repository = repository,
                faceDetectionEngine = faceDetectionEngine,
                featureExtractionEngine = featureExtractionEngine,
                tensorFlowEngine = tensorFlowEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}