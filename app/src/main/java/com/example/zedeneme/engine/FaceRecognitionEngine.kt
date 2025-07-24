package com.example.zedeneme.engine

import com.example.zedeneme.data.FaceFeatures
import com.example.zedeneme.data.RecognitionResult
import com.example.zedeneme.data.FaceAngle
import com.example.zedeneme.repository.FaceRepository
import com.google.gson.Gson
import kotlin.math.*

class FaceRecognitionEngine(
    private val repository: FaceRepository,
    private val featureExtractor: FeatureExtractionEngine
) {

    private val gson = Gson()

    companion object {
        private const val RECOGNITION_THRESHOLD = 0.65f
        private const val SAME_ANGLE_BOOST = 0.15f
        private const val DIFFERENT_ANGLE_PENALTY = 0.1f
    }

    suspend fun recognizeFace(
        features: FloatArray,
        currentAngle: FaceAngle
    ): RecognitionResult? {

        val allProfileFeatures = repository.getAllFeaturesForRecognition()
        if (allProfileFeatures.isEmpty()) return null

        val angleType = currentAngle.getAngleType()
        var bestMatch: Pair<String, Float>? = null
        var bestConfidence = 0f
        var bestMatchedAngle = angleType

        allProfileFeatures.forEach { (profileId, profileFeatures) ->
            // Önce aynı açıdaki örneklerle karşılaştır (boost ile)
            val sameAngleFeatures = profileFeatures.filter { it.angle == angleType }

            sameAngleFeatures.forEach { storedFeature ->
                try {
                    val storedFeatureArray = gson.fromJson(storedFeature.features, FloatArray::class.java)
                    val similarity = calculateCombinedSimilarity(features, storedFeatureArray)
                    val boostedSimilarity = similarity + SAME_ANGLE_BOOST

                    if (boostedSimilarity > bestConfidence) {
                        bestConfidence = boostedSimilarity
                        bestMatch = Pair(profileId, boostedSimilarity)
                        bestMatchedAngle = angleType
                    }
                } catch (e: Exception) {
                    // JSON parse hatası durumunda devam et
                }
            }

            // Diğer açılardaki örneklerle de karşılaştır (penalty ile)
            val otherAngleFeatures = profileFeatures.filter { it.angle != angleType }

            otherAngleFeatures.forEach { storedFeature ->
                try {
                    val storedFeatureArray = gson.fromJson(storedFeature.features, FloatArray::class.java)
                    val similarity = calculateCombinedSimilarity(features, storedFeatureArray)
                    val penalizedSimilarity = similarity - DIFFERENT_ANGLE_PENALTY

                    if (penalizedSimilarity > bestConfidence) {
                        bestConfidence = penalizedSimilarity
                        bestMatch = Pair(profileId, penalizedSimilarity)
                        bestMatchedAngle = storedFeature.angle
                    }
                } catch (e: Exception) {
                    // JSON parse hatası durumunda devam et
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

    // Kombine benzerlik hesaplama
    private fun calculateCombinedSimilarity(features1: FloatArray, features2: FloatArray): Float {
        if (features1.size != features2.size) return 0f

        val cosine = calculateCosineSimilarity(features1, features2) * 0.5f
        val euclidean = calculateEuclideanSimilarity(features1, features2) * 0.3f
        val manhattan = calculateManhattanSimilarity(features1, features2) * 0.2f

        return cosine + euclidean + manhattan
    }

    // Cosine similarity
    private fun calculateCosineSimilarity(features1: FloatArray, features2: FloatArray): Float {
        val dotProduct = features1.zip(features2) { a, b -> a * b }.sum()
        val magnitude1 = sqrt(features1.sumOf { (it * it).toDouble() }).toFloat()
        val magnitude2 = sqrt(features2.sumOf { (it * it).toDouble() }).toFloat()

        return if (magnitude1 > 0 && magnitude2 > 0) {
            dotProduct / (magnitude1 * magnitude2)
        } else {
            0f
        }
    }

    // Euclidean distance similarity
    private fun calculateEuclideanSimilarity(features1: FloatArray, features2: FloatArray): Float {
        val distance = sqrt(
            features1.zip(features2) { a, b ->
                (a - b).pow(2)
            }.sum().toDouble()
        ).toFloat()

        // Distance'ı similarity'ye çevir
        return 1f / (1f + distance)
    }

    // Manhattan distance similarity
    private fun calculateManhattanSimilarity(features1: FloatArray, features2: FloatArray): Float {
        val distance = features1.zip(features2) { a, b -> abs(a - b) }.sum()
        return 1f / (1f + distance)
    }

    // Pearson correlation similarity
    private fun calculatePearsonSimilarity(features1: FloatArray, features2: FloatArray): Float {
        val n = features1.size
        if (n == 0) return 0f

        val mean1 = features1.average().toFloat()
        val mean2 = features2.average().toFloat()

        var numerator = 0f
        var sumSq1 = 0f
        var sumSq2 = 0f

        for (i in features1.indices) {
            val diff1 = features1[i] - mean1
            val diff2 = features2[i] - mean2
            numerator += diff1 * diff2
            sumSq1 += diff1 * diff1
            sumSq2 += diff2 * diff2
        }

        val denominator = sqrt(sumSq1 * sumSq2)
        return if (denominator > 0) {
            (numerator / denominator + 1f) / 2f  // -1,1 arası değeri 0,1 arası yapar
        } else {
            0f
        }
    }

    // Batch recognition için
    suspend fun recognizeMultipleFaces(
        faceFeaturesList: List<Pair<FloatArray, FaceAngle>>
    ): List<RecognitionResult?> {
        return faceFeaturesList.map { (features, angle) ->
            recognizeFace(features, angle)
        }
    }

    // Confidence threshold'u dinamik olarak ayarla
    fun adjustThreshold(currentAccuracy: Float): Float {
        return when {
            currentAccuracy > 0.9f -> RECOGNITION_THRESHOLD + 0.1f
            currentAccuracy < 0.7f -> RECOGNITION_THRESHOLD - 0.1f
            else -> RECOGNITION_THRESHOLD
        }.coerceIn(0.3f, 0.9f)
    }

    // Feature quality check
    fun isFeatureQualityGood(features: FloatArray): Boolean {
        // NaN veya infinite değer kontrolü
        if (features.any { it.isNaN() || it.isInfinite() }) return false

        // Variance kontrolü (çok düşük variance kötü kalite işareti)
        val mean = features.average().toFloat()
        val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()

        return variance > 0.001f  // Minimum variance threshold
    }
}