package com.example.zedeneme.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.zedeneme.data.FaceLandmarks
import com.example.zedeneme.data.Point
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

    fun extractCombinedFeatures(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray {
        return try {
            val tensorFlowFeatures = tensorFlowEngine.extractFeatures(bitmap)
            if (tensorFlowFeatures != null && tensorFlowEngine.isFeatureQualityGood(tensorFlowFeatures)) {
                tensorFlowFeatures
            } else {
                extractLegacyFeatures(bitmap, landmarks)
            }
        } catch (e: Exception) {
            extractLegacyFeatures(bitmap, landmarks)
        }
    }

    private fun extractLegacyFeatures(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray {
        val lbpFeatures = extractLBPFeatures(bitmap)
        val hogFeatures = extractHOGFeatures(bitmap)
        val geometricFeatures = extractGeometricFeatures(landmarks)
        val combined = lbpFeatures + hogFeatures + geometricFeatures
        return normalizeFeatures(combined)
    }

    private fun extractLBPFeatures(bitmap: Bitmap): FloatArray {
        val grayBitmap = convertToGrayscale(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(grayBitmap, RESIZE_WIDTH, RESIZE_HEIGHT, true)
        val lbpValues = calculateLBP(resizedBitmap)
        return calculateHistogram(lbpValues, FEATURE_SIZE_LBP)
    }

    private fun extractHOGFeatures(bitmap: Bitmap): FloatArray {
        val grayBitmap = convertToGrayscale(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(grayBitmap, RESIZE_WIDTH, RESIZE_HEIGHT, true)
        val gradients = calculateGradients(resizedBitmap)
        return calculateHOG(gradients)
    }

    private fun extractGeometricFeatures(landmarks: FaceLandmarks): FloatArray {
        if (landmarks.points.size < 3) return FloatArray(FEATURE_SIZE_GEOMETRIC) { 0f }
        val features = mutableListOf<Float>()
        try {
            if (landmarks.points.size >= 2) {
                features.add(calculateDistance(landmarks.points[0], landmarks.points[1]) / landmarks.boundingBox.width())
            }
            if (landmarks.points.size >= 4) {
                features.add(calculateDistance(landmarks.points[2], landmarks.points[3]) / landmarks.boundingBox.height())
            }
            features.add(landmarks.boundingBox.width() / landmarks.boundingBox.height())
            features.addAll(calculateSymmetryFeatures(landmarks))
            features.addAll(calculateCenterFeatures(landmarks))
            if (landmarks.points.size >= 5) {
                features.add(calculateDistance(landmarks.points[3], landmarks.points[4]) / landmarks.boundingBox.width())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geometric features extraction error", e)
        }
        while (features.size < FEATURE_SIZE_GEOMETRIC) features.add(0f)
        return features.take(FEATURE_SIZE_GEOMETRIC).toFloatArray()
    }

    private fun calculateSymmetryFeatures(landmarks: FaceLandmarks): List<Float> {
        val features = mutableListOf<Float>()
        try {
            if (landmarks.points.size >= 2) {
                features.add(abs(landmarks.points[0].y - landmarks.points[1].y) / landmarks.boundingBox.height())
            }
            if (landmarks.points.size >= 5) {
                features.add(abs(landmarks.points[3].y - landmarks.points[4].y) / landmarks.boundingBox.height())
            }
            val centerX = landmarks.boundingBox.centerX()
            if (landmarks.points.size >= 2) {
                features.add(abs(abs(landmarks.points[0].x - centerX) - abs(landmarks.points[1].x - centerX)) / landmarks.boundingBox.width())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Symmetry features calculation error", e)
        }
        while (features.size < 5) features.add(0f)
        return features.take(5)
    }

    private fun calculateCenterFeatures(landmarks: FaceLandmarks): List<Float> {
        val features = mutableListOf<Float>()
        try {
            val centerX = landmarks.boundingBox.centerX()
            val centerY = landmarks.boundingBox.centerY()
            landmarks.points.take(5).forEach { point ->
                features.add(calculateDistance(point, Point(centerX, centerY)) / landmarks.boundingBox.width())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Center features calculation error", e)
        }
        while (features.size < 5) features.add(0f)
        return features.take(5)
    }

    private fun calculateDistance(point1: Point, point2: Point): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateGradients(bitmap: Bitmap): Array<Array<Point>> {
        val width = bitmap.width
        val height = bitmap.height
        val gradients = Array(height) { Array(width) { Point(0f, 0f) } }
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = Color.red(bitmap.getPixel(x + 1, y)) - Color.red(bitmap.getPixel(x - 1, y))
                val gy = Color.red(bitmap.getPixel(x, y + 1)) - Color.red(bitmap.getPixel(x, y - 1))
                gradients[y][x] = Point(gx.toFloat(), gy.toFloat())
            }
        }
        return gradients
    }

    private fun calculateHOG(gradients: Array<Array<Point>>): FloatArray {
        val cellSize = 8
        val numBins = 9
        val height = gradients.size
        val width = gradients[0].size
        val cellsY = height / cellSize
        val cellsX = width / cellSize
        val cellHistograms = Array(cellsY) { Array(cellsX) { FloatArray(numBins) } }

        for (cy in 0 until cellsY) {
            for (cx in 0 until cellsX) {
                for (y in cy * cellSize until min((cy + 1) * cellSize, height)) {
                    for (x in cx * cellSize until min((cx + 1) * cellSize, width)) {
                        val gx = gradients[y][x].x
                        val gy = gradients[y][x].y
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

    private fun calculateLBP(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val lbpValues = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centerPixel = Color.red(bitmap.getPixel(x, y))
                var lbpValue = 0
                val neighbors = arrayOf(
                    Point(-1f, -1f), Point(-1f, 0f), Point(-1f, 1f),
                    Point(0f, 1f), Point(1f, 1f), Point(1f, 0f),
                    Point(1f, -1f), Point(0f, -1f)
                )
                neighbors.forEachIndexed { index, it ->
                    val neighborPixel = Color.red(bitmap.getPixel(x + it.x.toInt(), y + it.y.toInt()))
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
        val total = histogram.sum()
        if (total > 0) {
            for (i in histogram.indices) {
                histogram[i] /= total
            }
        }
        return histogram
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                grayBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        return grayBitmap
    }

    private fun normalizeFeatures(features: FloatArray): FloatArray {
        val mean = features.average().toFloat()
        val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)
        return if (stdDev > 0) features.map { (it - mean) / stdDev }.toFloatArray() else features
    }

    public fun getFeatureType(features: FloatArray): String {
        return when (features.size) {
            512 -> "TensorFlow Lite (512D)"
            420 -> "Legacy Combined (LBP+HOG+Geometric)"
            else -> "Unknown (${features.size}D)"
        }
    }

    fun assessFeatureQuality(features: FloatArray): Float {
        return if (tensorFlowEngine.isFeatureQualityGood(features)) {
            1.0f // Yüksek kalite
        } else {
            0.5f // Düşük kalite
        }
    }
}