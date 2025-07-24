package com.example.zedeneme.data

import android.graphics.RectF
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.abs

@Entity(tableName = "face_profiles")
data class FaceProfile(
    @PrimaryKey
    val id: String,
    val personName: String,
    val frontalFeatures: String,
    val leftProfileFeatures: String,
    val rightProfileFeatures: String,
    val upAngleFeatures: String,
    val downAngleFeatures: String,
    val registrationDate: Long,
    val isComplete: Boolean = false
)

@Entity(tableName = "face_features")
data class FaceFeatures(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: String,
    val angle: String,
    val features: String,           // JSON encoded FloatArray
    val landmarks: String,          // JSON encoded landmarks
    val confidence: Float,
    val timestamp: Long
)

data class FaceAngle(
    val yaw: Float,    // Sağ-sol dönüş
    val pitch: Float,  // Yukarı-aşağı
    val roll: Float    // Eğim
) {
    fun getAngleType(): String {
        return when {
            abs(yaw) < 15 && abs(pitch) < 15 -> "frontal"
            yaw > 15 -> "right_profile"
            yaw < -15 -> "left_profile"
            pitch > 15 -> "up_angle"
            pitch < -15 -> "down_angle"
            else -> "mixed_angle"
        }
    }

    fun isValidAngle(): Boolean {
        return abs(yaw) < 60 && abs(pitch) < 45
    }
}

data class FaceLandmarks(
    val points: List<Pair<Float, Float>>,
    val boundingBox: RectF,
    val confidence: Float
)

data class RecognitionResult(
    val personId: String,
    val personName: String,
    val confidence: Float,
    val matchedAngle: String,
    val timestamp: Long
)

data class DetectedFace(
    val boundingBox: android.graphics.Rect,
    val landmarks: FaceLandmarks,
    val angle: FaceAngle,
    val confidence: Float,
    val bitmap: android.graphics.Bitmap
)

// UI State classes
data class RegistrationState(
    val isRegistering: Boolean = false,
    val currentAngle: String = "",
    val completedAngles: Set<String> = emptySet(),
    val currentInstruction: String = "",
    val profileId: String? = null,
    val personName: String = "",
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null,
    val detectedFace: DetectedFace? = null
)

data class RecognitionState(
    val isRecognizing: Boolean = false,
    val lastResult: RecognitionResult? = null,
    val detectedFace: DetectedFace? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

data class HomeState(
    val registeredPeople: List<FaceProfile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)