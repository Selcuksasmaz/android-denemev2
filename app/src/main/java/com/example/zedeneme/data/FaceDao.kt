package com.example.zedeneme.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {

    @Query("SELECT * FROM face_profiles WHERE isComplete = 1")
    fun getAllCompleteProfiles(): Flow<List<FaceProfile>>

    @Query("SELECT * FROM face_profiles")
    fun getAllProfiles(): Flow<List<FaceProfile>>

    @Query("SELECT * FROM face_profiles WHERE id = :profileId")
    suspend fun getProfile(profileId: String): FaceProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FaceProfile)

    @Update
    suspend fun updateProfile(profile: FaceProfile)

    @Delete
    suspend fun deleteProfile(profile: FaceProfile)

    @Query("SELECT * FROM face_features WHERE profileId = :profileId")
    suspend fun getFeaturesForProfile(profileId: String): List<FaceFeatures>

    @Query("SELECT * FROM face_features WHERE profileId = :profileId AND angle = :angle")
    suspend fun getFeaturesForAngle(profileId: String, angle: String): List<FaceFeatures>

    @Insert
    suspend fun insertFeatures(features: FaceFeatures)

    @Query("DELETE FROM face_features WHERE profileId = :profileId")
    suspend fun deleteFeaturesForProfile(profileId: String)

    @Query("SELECT COUNT(*) FROM face_features WHERE profileId = :profileId")
    suspend fun getFeatureCount(profileId: String): Int

    @Query("SELECT DISTINCT angle FROM face_features WHERE profileId = :profileId")
    suspend fun getRegisteredAngles(profileId: String): List<String>

    @Query("DELETE FROM face_features WHERE id = :featureId")
    suspend fun deleteFeature(featureId: Long)
}