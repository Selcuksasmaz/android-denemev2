package com.example.zedeneme.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.zedeneme.data.RegistrationState
import com.example.zedeneme.data.DetectedFace
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.repository.FaceRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class FaceRegistrationViewModel(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationState())
    val uiState: StateFlow<RegistrationState> = _uiState.asStateFlow()

    private val gson = Gson()
    private var isProcessingFrame = false
    private var lastProcessTime = 0L
    private val MIN_PROCESS_INTERVAL = 500L // 500ms aralıkla işle

    companion object {
        private const val TAG = "FaceRegistration"
    }

    fun startRegistration(personName: String) {
        Log.d(TAG, "Registration başlatılıyor: $personName")

        if (personName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Lütfen bir isim girin"
            )
            return
        }

        viewModelScope.launch {
            try {
                val profileId = repository.createNewProfile(personName.trim())
                val nextAngle = repository.getNextRequiredAngle(emptySet())
                val instruction = repository.getAngleInstructions()[nextAngle] ?: "Kameraya bakın"

                Log.d(TAG, "Profile oluşturuldu: $profileId, İlk açı: $nextAngle")

                _uiState.value = RegistrationState(
                    isRegistering = true,
                    currentAngle = nextAngle ?: "frontal",
                    currentInstruction = instruction,
                    profileId = profileId,
                    personName = personName.trim(),
                    progress = 0f,
                    completedAngles = emptySet()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Registration başlatma hatası", e)
                _uiState.value = _uiState.value.copy(
                    error = "Kayıt başlatılamadı: ${e.message}"
                )
            }
        }
    }

    fun processFrame(bitmap: Bitmap) {
        val currentState = _uiState.value
        if (!currentState.isRegistering || currentState.profileId == null || isProcessingFrame) {
            return
        }

        // Frame rate sınırlaması
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL) {
            return
        }

        isProcessingFrame = true
        lastProcessTime = currentTime

        viewModelScope.launch {
            try {
                Log.d(TAG, "Frame işleniyor...")

                // Face detection
                val detectedFaces = faceDetectionEngine.detectFaces(bitmap)
                Log.d(TAG, "Tespit edilen yüz sayısı: ${detectedFaces.size}")

                if (detectedFaces.isEmpty()) {
                    _uiState.value = currentState.copy(
                        detectedFace = null,
                        currentInstruction = "Yüz bulunamadı - Kameraya daha yakın olun"
                    )
                    isProcessingFrame = false
                    return@launch
                }

                val bestFace = detectedFaces.maxByOrNull { it.confidence }
                Log.d(TAG, "En iyi yüz confidence: ${bestFace?.confidence}")

                if (bestFace != null) {
                    val detectedAngle = bestFace.angle.getAngleType()
                    val targetAngle = currentState.currentAngle

                    Log.d(TAG, "Tespit edilen açı: $detectedAngle, Hedef açı: $targetAngle")
                    Log.d(TAG, "Yüz açıları - Yaw: ${bestFace.angle.yaw}, Pitch: ${bestFace.angle.pitch}")

                    // Yüz kalitesi kontrolü
                    val isQualityGood = faceDetectionEngine.isQualityGoodEnough(bestFace)
                    Log.d(TAG, "Yüz kalitesi: $isQualityGood")

                    if (isQualityGood) {
                        _uiState.value = currentState.copy(
                            detectedFace = bestFace,
                            currentInstruction = getDetailedInstruction(detectedAngle, targetAngle, bestFace)
                        )

                        // Otomatik kayıt - açı uyumlu ve kalite yeterli ise
                        if (isAngleMatch(detectedAngle, targetAngle) && bestFace.confidence > 0.7f) {
                            Log.d(TAG, "Otomatik kayıt başlatılıyor...")
                            saveFaceFeatures(bestFace, currentState.profileId, targetAngle)
                        }
                    } else {
                        _uiState.value = currentState.copy(
                            detectedFace = bestFace,
                            currentInstruction = "Yüz kalitesi düşük - Işık daha iyi olsun ve sabit durun"
                        )
                    }
                } else {
                    _uiState.value = currentState.copy(
                        detectedFace = null,
                        currentInstruction = "Net bir yüz tespit edilemedi"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Frame işleme hatası", e)
                _uiState.value = currentState.copy(
                    error = "Yüz işleme hatası: ${e.message}"
                )
            } finally {
                isProcessingFrame = false
            }
        }
    }

    private fun getDetailedInstruction(detectedAngle: String, targetAngle: String, face: DetectedFace): String {
        return when {
            detectedAngle == targetAngle && face.confidence > 0.7f -> {
                "✅ Mükemmel! Otomatik kaydediliyor..."
            }
            detectedAngle == targetAngle && face.confidence > 0.5f -> {
                "✅ İyi! Biraz daha sabit durun..."
            }
            detectedAngle == targetAngle -> {
                "⚠️ Açı doğru ama kalite düşük. Işığı iyileştirin."
            }
            targetAngle == "frontal" -> {
                when {
                    face.angle.yaw > 10 -> "👈 Başınızı sola çevirin"
                    face.angle.yaw < -10 -> "👉 Başınızı sağa çevirin"
                    face.angle.pitch > 10 -> "👇 Başınızı aşağı eğin"
                    face.angle.pitch < -10 -> "👆 Başınızı yukarı kaldırın"
                    else -> "📷 Düz bakın"
                }
            }
            targetAngle == "left_profile" -> {
                when {
                    face.angle.yaw < 20 -> "👈 Başınızı daha fazla sola çevirin"
                    face.angle.yaw > 60 -> "👉 Başınızı biraz sağa getirin"
                    else -> "✅ Sol profil pozisyonu iyi"
                }
            }
            targetAngle == "right_profile" -> {
                when {
                    face.angle.yaw > -20 -> "👉 Başınızı daha fazla sağa çevirin"
                    face.angle.yaw < -60 -> "👈 Başınızı biraz sola getirin"
                    else -> "✅ Sağ profil pozisyonu iyi"
                }
            }
            targetAngle == "up_angle" -> {
                when {
                    face.angle.pitch < 15 -> "👆 Başınızı daha fazla yukarı kaldırın"
                    face.angle.pitch > 45 -> "👇 Başınızı biraz aşağı getirin"
                    else -> "✅ Yukarı açı pozisyonu iyi"
                }
            }
            targetAngle == "down_angle" -> {
                when {
                    face.angle.pitch > -15 -> "👇 Başınızı daha fazla aşağı eğin"
                    face.angle.pitch < -45 -> "👆 Başınızı biraz yukarı getirin"
                    else -> "✅ Aşağı açı pozisyonu iyi"
                }
            }
            else -> "Pozisyonu ayarlayın"
        }
    }

    private fun isAngleMatch(detectedAngle: String, targetAngle: String): Boolean {
        return detectedAngle == targetAngle
    }

    private suspend fun saveFaceFeatures(detectedFace: DetectedFace, profileId: String, angle: String) {
        try {
            Log.d(TAG, "Feature extraction başlıyor...")

            val features = featureExtractionEngine.extractCombinedFeatures(
                detectedFace.bitmap,
                detectedFace.landmarks
            )

            Log.d(TAG, "Çıkarılan feature sayısı: ${features.size}")

            // Feature quality check
            if (features.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "Feature kalitesi düşük - NaN veya Infinite değerler var")
                _uiState.value = _uiState.value.copy(
                    error = "Feature kalitesi düşük, tekrar deneyin"
                )
                return
            }

            val landmarksJson = gson.toJson(detectedFace.landmarks)

            // Repository'ye kaydet
            repository.saveFeatures(profileId, angle, features, landmarksJson)

            Log.d(TAG, "Features kaydedildi: $angle")

            val currentState = _uiState.value
            val newCompletedAngles = currentState.completedAngles + angle
            val nextAngle = repository.getNextRequiredAngle(newCompletedAngles)
            val progress = repository.getCompletionProgress(newCompletedAngles)

            Log.d(TAG, "İlerleme: ${newCompletedAngles.size}/5, Sonraki açı: $nextAngle")

            if (nextAngle != null) {
                // Bir sonraki açıya geç
                val instruction = repository.getAngleInstructions()[nextAngle] ?: ""
                _uiState.value = currentState.copy(
                    currentAngle = nextAngle,
                    completedAngles = newCompletedAngles,
                    currentInstruction = "✅ $angle kaydedildi! $instruction",
                    progress = progress,
                    detectedFace = null
                )

                // 2 saniye sonra normal instruction'a dön
                delay(2000)
                _uiState.value = _uiState.value.copy(
                    currentInstruction = instruction
                )
            } else {
                // Kayıt tamamlandı
                Log.d(TAG, "Tüm açılar tamamlandı!")
                _uiState.value = currentState.copy(
                    isComplete = true,
                    progress = 1f,
                    currentInstruction = "🎉 Kayıt başarıyla tamamlandı!"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Feature kaydetme hatası", e)
            _uiState.value = _uiState.value.copy(
                error = "Özellik kaydetme hatası: ${e.message}"
            )
        }
    }

    fun forceCapture() {
        Log.d(TAG, "Manuel kayıt tetiklendi")
        val currentState = _uiState.value
        currentState.detectedFace?.let { face ->
            currentState.profileId?.let { profileId ->
                viewModelScope.launch {
                    saveFaceFeatures(face, profileId, currentState.currentAngle)
                }
            }
        }
    }

    fun skipCurrentAngle() {
        Log.d(TAG, "Açı atlandı")
        val currentState = _uiState.value
        val newCompletedAngles = currentState.completedAngles + currentState.currentAngle
        val nextAngle = repository.getNextRequiredAngle(newCompletedAngles)
        val progress = repository.getCompletionProgress(newCompletedAngles)

        if (nextAngle != null) {
            val instruction = repository.getAngleInstructions()[nextAngle] ?: ""
            _uiState.value = currentState.copy(
                currentAngle = nextAngle,
                completedAngles = newCompletedAngles,
                currentInstruction = instruction,
                progress = progress
            )
        } else {
            _uiState.value = currentState.copy(
                isComplete = true,
                progress = 1f,
                currentInstruction = "Kayıt tamamlandı! (Bazı açılar atlandı)"
            )
        }
    }

    fun resetRegistration() {
        Log.d(TAG, "Registration sıfırlandı")
        _uiState.value = RegistrationState()
        isProcessingFrame = false
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Debug bilgileri
    fun getDebugInfo(): String {
        val state = _uiState.value
        return """
            Kayıt durumu: ${state.isRegistering}
            Mevcut açı: ${state.currentAngle}
            Tamamlanan açılar: ${state.completedAngles}
            İlerleme: ${(state.progress * 100).toInt()}%
            Tespit edilen yüz: ${state.detectedFace != null}
            Hata: ${state.error ?: "Yok"}
        """.trimIndent()
    }
}

class FaceRegistrationViewModelFactory(
    private val repository: FaceRepository,
    private val faceDetectionEngine: FaceDetectionEngine,
    private val featureExtractionEngine: FeatureExtractionEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRegistrationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FaceRegistrationViewModel(repository, faceDetectionEngine, featureExtractionEngine) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}