package com.example.zedeneme.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.zedeneme.data.FaceLandmarks
import kotlin.math.*

class FeatureExtractionEngine(
    private val tensorFlowEngine: TensorFlowFaceRecognition
) {

    companion object {
        private const val TAG = "FeatureExtraction"
        private const val FEATURE_SIZE_LBP = 256
        private const val FEATURE_SIZE_HOG = 144
        private const val FEATURE_SIZE_GEOMETRIC = 20
        private const val RESIZE_WIDTH = 64
        private const val RESIZE_HEIGHT = 64
    }

    // Ana feature extraction fonksiyonu - TensorFlow Lite ile güncellenmiş
    fun extractCombinedFeatures(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray {
        return try {
            Log.d(TAG, "🚀 TensorFlow Lite ile feature extraction başlıyor...")

            // Önce TensorFlow Lite kullanmayı dene
            val tensorFlowFeatures = tensorFlowEngine.extractFeatures(bitmap)

            if (tensorFlowFeatures != null && tensorFlowEngine.isFeatureQualityGood(tensorFlowFeatures)) {
                Log.d(TAG, "✅ TensorFlow features başarıyla çıkarıldı: ${tensorFlowFeatures.size} boyut")
                tensorFlowFeatures
            } else {
                Log.w(TAG, "⚠️ TensorFlow features kalitesiz veya null, legacy metodlar kullanılıyor")
                extractLegacyFeatures(bitmap, landmarks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ TensorFlow feature extraction hatası, fallback kullanılıyor", e)
            extractLegacyFeatures(bitmap, landmarks)
        }
    }

    // Legacy feature extraction (fallback olarak)
    private fun extractLegacyFeatures(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray {
        Log.d(TAG, "🔄 Legacy feature extraction kullanılıyor")

        val lbpFeatures = extractLBPFeatures(bitmap)
        val hogFeatures = extractHOGFeatures(bitmap)
        val geometricFeatures = extractGeometricFeatures(landmarks)

        // Özellikleri birleştir ve normalize et
        val combined = lbpFeatures + hogFeatures + geometricFeatures
        return normalizeFeatures(combined)
    }

    // LBP (Local Binary Pattern) özellik çıkarma - legacy
    private fun extractLBPFeatures(bitmap: Bitmap): FloatArray {
        val grayBitmap = convertToGrayscale(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(grayBitmap, RESIZE_WIDTH, RESIZE_HEIGHT, true)

        val lbpValues = calculateLBP(resizedBitmap)
        val histogram = calculateHistogram(lbpValues, FEATURE_SIZE_LBP)

        return histogram
    }

    // HOG (Histogram of Oriented Gradients) özellik çıkarma - legacy
    private fun extractHOGFeatures(bitmap: Bitmap): FloatArray {
        val grayBitmap = convertToGrayscale(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(grayBitmap, RESIZE_WIDTH, RESIZE_HEIGHT, true)

        val gradients = calculateGradients(resizedBitmap)
        val hogFeatures = calculateHOG(gradients)

        return hogFeatures
    }

    // Landmark tabanlı geometrik özellikler - güncellenmiş landmark indexleri
    private fun extractGeometricFeatures(landmarks: FaceLandmarks): FloatArray {
        if (landmarks.points.size < 3) return FloatArray(FEATURE_SIZE_GEOMETRIC) { 0f }

        val features = mutableListOf<Float>()

        try {
            // Göz arası mesafe (index 0: sol göz, index 1: sağ göz)
            if (landmarks.points.size >= 2) {
                val eyeDistance = calculateDistance(landmarks.points[0], landmarks.points[1])
                val normalizedEyeDistance = eyeDistance / landmarks.boundingBox.width()
                features.add(normalizedEyeDistance)
            }

            // Burun-ağız mesafesi (index 2: burun, index 3: ağız sol)
            if (landmarks.points.size >= 4) {
                val noseMouthDistance = calculateDistance(landmarks.points[2], landmarks.points[3])
                val normalizedNoseMouth = noseMouthDistance / landmarks.boundingBox.height()
                features.add(normalizedNoseMouth)
            }

            // Yüz en-boy oranı
            val aspectRatio = landmarks.boundingBox.width() / landmarks.boundingBox.height()
            features.add(aspectRatio)

            // Simetri özellikleri - güncellenmiş indexler
            val symmetryFeatures = calculateSymmetryFeatures(landmarks)
            features.addAll(symmetryFeatures)

            // Yüz merkezi özellikleri
            val centerFeatures = calculateCenterFeatures(landmarks)
            features.addAll(centerFeatures)

            // Ağız genişliği (index 3: ağız sol, index 4: ağız sağ)
            if (landmarks.points.size >= 5) {
                val mouthWidth = calculateDistance(landmarks.points[3], landmarks.points[4])
                val normalizedMouthWidth = mouthWidth / landmarks.boundingBox.width()
                features.add(normalizedMouthWidth)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Geometric features extraction hatası", e)
        }

        // 20 özelliğe tamamla veya kısalt
        while (features.size < FEATURE_SIZE_GEOMETRIC) {
            features.add(0f)
        }

        return features.take(FEATURE_SIZE_GEOMETRIC).toFloatArray()
    }

    // TensorFlow Lite özellik kalitesi kontrolü
    fun assessFeatureQuality(features: FloatArray): Float {
        return if (tensorFlowEngine.isFeatureQualityGood(features)) {
            1.0f // Yüksek kalite
        } else {
            0.5f // Düşük kalite
        }
    }

    // Batch processing için
    fun extractFeaturesForBatch(bitmaps: List<Bitmap>, landmarksList: List<FaceLandmarks>): List<FloatArray> {
        require(bitmaps.size == landmarksList.size) { "Bitmap ve landmarks listesi aynı boyutta olmalı" }

        return bitmaps.zip(landmarksList) { bitmap, landmarks ->
            extractCombinedFeatures(bitmap, landmarks)
        }
    }

    // Feature türünü döndür (debugging için)
    fun getFeatureType(features: FloatArray): String {
        return when (features.size) {
            512 -> "TensorFlow Lite (512D)"
            420 -> "Legacy Combined (LBP+HOG+Geometric)"
            256 -> "LBP Only"
            144 -> "HOG Only"
            20 -> "Geometric Only"
            else -> "Unknown (${features.size}D)"
        }
    }

    // Feature istatistikleri
    fun getFeatureStats(features: FloatArray): Map<String, Float> {
        return mapOf(
            "dimensions" to features.size.toFloat(),
            "mean" to features.average().toFloat(),
            "std" to sqrt(features.map { (it - features.average()) * (it - features.average()) }.average()).toFloat(),
            "min" to features.minOrNull() ?: 0f,
            "max" to features.maxOrNull() ?: 0f,
            "quality" to assessFeatureQuality(features)
        )
    }

    private fun calculateSymmetryFeatures(landmarks: FaceLandmarks): List<Float> {
        val features = mutableListOf<Float>()

        try {
            if (landmarks.points.size >= 2) {
                // Sol ve sağ göz y koordinatları arasındaki fark (simetri)
                val eyeSymmetry = abs(landmarks.points[0].second - landmarks.points[1].second)
                val normalizedEyeSymmetry = eyeSymmetry / landmarks.boundingBox.height()
                features.add(normalizedEyeSymmetry)
            }

            if (landmarks.points.size >= 5) {
                // Sol ve sağ ağız köşesi simetrisi (index 3: ağız sol, index 4: ağız sağ)
                val mouthSymmetry = abs(landmarks.points[3].second - landmarks.points[4].second)
                val normalizedMouthSymmetry = mouthSymmetry / landmarks.boundingBox.height()
                features.add(normalizedMouthSymmetry)
            }

            // Yüz merkezi etrafında simetri
            val centerX = landmarks.boundingBox.centerX()
            if (landmarks.points.size >= 2) {
                val leftEyeDistance = abs(landmarks.points[0].first - centerX)
                val rightEyeDistance = abs(landmarks.points[1].first - centerX)
                val eyeCenterSymmetry = abs(leftEyeDistance - rightEyeDistance) / landmarks.boundingBox.width()
                features.add(eyeCenterSymmetry)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Simetri özellikleri hesaplama hatası", e)
        }

        // 5 simetri özelliğine tamamla
        while (features.size < 5) {
            features.add(0f)
        }

        return features.take(5)
    }

    private fun calculateCenterFeatures(landmarks: FaceLandmarks): List<Float> {
        val features = mutableListOf<Float>()

        try {
            val centerX = landmarks.boundingBox.centerX()
            val centerY = landmarks.boundingBox.centerY()

            // Her landmark'ın merkeze olan uzaklığı (ilk 5 landmark)
            landmarks.points.take(5).forEach { point ->
                val distance = calculateDistance(point, Pair(centerX, centerY))
                val normalizedDistance = distance / landmarks.boundingBox.width()
                features.add(normalizedDistance)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Merkez özellikleri hesaplama hatası", e)
        }

        // 5 merkez özelliğine tamamla
        while (features.size < 5) {
            features.add(0f)
        }

        return features.take(5)
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                val grayPixel = Color.rgb(gray, gray, gray)
                grayBitmap.setPixel(x, y, grayPixel)
            }
        }

        return grayBitmap
    }

    private fun calculateLBP(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val lbpValues = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centerPixel = Color.red(bitmap.getPixel(x, y))
                var lbpValue = 0

                // 8 komşu pikseli saat yönünde kontrol et
                val neighbors = arrayOf(
                    Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
                    Pair(0, 1), Pair(1, 1), Pair(1, 0),
                    Pair(1, -1), Pair(0, -1)
                )

                neighbors.forEachIndexed { index, (dx, dy) ->
                    val neighborPixel = Color.red(bitmap.getPixel(x + dx, y + dy))
                    if (neighborPixel >= centerPixel) {
                        lbpValue = lbpValue or (1 shl index)
                    }
                }

                lbpValues[y * width + x] = lbpValue
            }
        }

        return lbpValues
    }

    private fun calculateHistogram(values: IntArray, bins: Int): FloatArray {
        val histogram = FloatArray(bins)
        values.forEach { value ->
            if (value in 0 until bins) {
                histogram[value]++
            }
        }

        // Normalize histogram
        val total = histogram.sum()
        if (total > 0) {
            for (i in histogram.indices) {
                histogram[i] = histogram[i] / total
            }
        }

        return histogram
    }

    private fun calculateGradients(bitmap: Bitmap): Array<Array<Pair<Float, Float>>> {
        val width = bitmap.width
        val height = bitmap.height
        val gradients = Array(height) { Array(width) { Pair(0f, 0f) } }

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = Color.red(bitmap.getPixel(x + 1, y)) - Color.red(bitmap.getPixel(x - 1, y))
                val gy = Color.red(bitmap.getPixel(x, y + 1)) - Color.red(bitmap.getPixel(x, y - 1))
                gradients[y][x] = Pair(gx.toFloat(), gy.toFloat())
            }
        }

        return gradients
    }

    private fun calculateHOG(gradients: Array<Array<Pair<Float, Float>>>): FloatArray {
        val cellSize = 8
        val numBins = 9

        val height = gradients.size
        val width = gradients[0].size

        val cellsY = height / cellSize
        val cellsX = width / cellSize

        val cellHistograms = Array(cellsY) { Array(cellsX) { FloatArray(numBins) } }

        // Her hücre için histogram hesapla
        for (cy in 0 until cellsY) {
            for (cx in 0 until cellsX) {
                for (y in cy * cellSize until min((cy + 1) * cellSize, height)) {
                    for (x in cx * cellSize until min((cx + 1) * cellSize, width)) {
                        val (gx, gy) = gradients[y][x]
                        val magnitude = sqrt(gx * gx + gy * gy)

                        if (magnitude > 0) {
                            val angle = atan2(gy, gx) * 180 / PI
                            val normalizedAngle = (angle + 180) % 180
                            val binIndex = (normalizedAngle / 20).toInt().coerceIn(0, numBins - 1)
                            cellHistograms[cy][cx][binIndex] += magnitude
                        }
                    }
                }
            }
        }

        // Features'ı topla ve normalize et
        val features = mutableListOf<Float>()
        for (cy in 0 until cellsY) {
            for (cx in 0 until cellsX) {
                val cellHist = cellHistograms[cy][cx]
                val norm = sqrt(cellHist.sumOf { (it * it).toDouble() }).toFloat()

                if (norm > 0) {
                    cellHist.forEach { features.add(it / norm) }
                } else {
                    cellHist.forEach { features.add(0f) }
                }
            }
        }

        return features.toFloatArray()
    }

    private fun calculateDistance(point1: Pair<Float, Float>, point2: Pair<Float, Float>): Float {
        val dx = point1.first - point2.first
        val dy = point1.second - point2.second
        return sqrt(dx * dx + dy * dy)
    }

    private fun normalizeFeatures(features: FloatArray): FloatArray {
        val mean = features.average().toFloat()
        val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)

        return if (stdDev > 0) {
            features.map { (it - mean) / stdDev }.toFloatArray()
        } else {
            features
        }
    }
}