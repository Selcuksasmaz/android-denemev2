package com.example.zedeneme.repository

import com.example.zedeneme.data.FaceDao
import com.example.zedeneme.data.FaceProfile
import com.example.zedeneme.data.FaceFeatures
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import com.google.gson.Gson

class FaceRepository(private val faceDao: FaceDao) {

    private val gson = Gson()

    fun getAllProfiles(): Flow<List<FaceProfile>> = faceDao.getAllCompleteProfiles()

    fun getAllProfilesIncludingIncomplete(): Flow<List<FaceProfile>> = faceDao.getAllProfiles()

    suspend fun getProfile(profileId: String): FaceProfile? = faceDao.getProfile(profileId)

    suspend fun insertFullProfile(profile: FaceProfile) {
        faceDao.insertProfile(profile)
    }

    suspend fun saveFeatures(profileId: String, angle: String, features: FloatArray, landmarks: String) {
        val featuresJson = gson.toJson(features)
        val faceFeatures = FaceFeatures(
            profileId = profileId,
            angle = angle,
            features = featuresJson,
            landmarks = landmarks,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis()
        )
        faceDao.insertFeatures(faceFeatures)

        // Profile completeness'ı kontrol et
        updateProfileCompleteness(profileId)
    }

    private suspend fun updateProfileCompleteness(profileId: String) {
        val registeredAngles = faceDao.getRegisteredAngles(profileId)
        val requiredAngles = getRequiredAngles()

        if (registeredAngles.containsAll(requiredAngles)) {
            val profile = faceDao.getProfile(profileId)
            profile?.let {
                val updatedProfile = it.copy(isComplete = true)
                faceDao.updateProfile(updatedProfile)
            }
        }
    }

    suspend fun getAllFeaturesForRecognition(): Map<String, List<FaceFeatures>> {
        val result = mutableMapOf<String, List<FaceFeatures>>()

        val profiles = faceDao.getAllCompleteProfiles()
        profiles.collect { profileList ->
            profileList.forEach { profile ->
                val features = faceDao.getFeaturesForProfile(profile.id)
                if (features.isNotEmpty()) {
                    result[profile.id] = features
                }
            }
        }

        return result
    }

    suspend fun deleteProfile(profileId: String) {
        val profile = faceDao.getProfile(profileId)
        profile?.let {
            faceDao.deleteFeaturesForProfile(profileId)
            faceDao.deleteProfile(it)
        }
    }

    suspend fun getRegisteredAngles(profileId: String): List<String> {
        return faceDao.getRegisteredAngles(profileId)
    }

    suspend fun isAngleRegistered(profileId: String, angle: String): Boolean {
        val features = faceDao.getFeaturesForAngle(profileId, angle)
        return features.isNotEmpty()
    }

    fun getRequiredAngles(): List<String> {
        return listOf("frontal", "left_profile", "right_profile", "up_angle", "down_angle")
    }

    fun getAngleInstructions(): Map<String, String> {
        return mapOf(
            "frontal" to "Kameraya düz bakın",
            "left_profile" to "Başınızı sola çevirin",
            "right_profile" to "Başınızı sağa çevirin",
            "up_angle" to "Başınızı yukarı kaldırın",
            "down_angle" to "Başınızı aşağı eğin"
        )
    }

    fun getNextRequiredAngle(completedAngles: Set<String>): String? {
        val requiredAngles = getRequiredAngles()
        return requiredAngles.firstOrNull { !completedAngles.contains(it) }
    }

    fun getCompletionProgress(completedAngles: Set<String>): Float {
        val totalRequired = getRequiredAngles().size
        return completedAngles.size.toFloat() / totalRequired.toFloat()
    }
}