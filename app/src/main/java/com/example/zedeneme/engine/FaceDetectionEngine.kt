package com.example.zedeneme.engine

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.example.zedeneme.data.FaceLandmarks
import com.example.zedeneme.data.FaceAngle
import com.example.zedeneme.data.DetectedFace
import com.example.zedeneme.data.Point
import kotlinx.coroutines.tasks.await

class FaceDetectionEngine {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(inputImage).await()

            faces.map { face ->
                DetectedFace(
                    boundingBox = face.boundingBox,
                    landmarks = extractLandmarks(face),
                    angle = calculateFaceAngle(face),
                    confidence = calculateConfidence(face),
                    bitmap = cropFaceBitmap(bitmap, face.boundingBox)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun detectSingleFace(bitmap: Bitmap): DetectedFace? {
        val faces = detectFaces(bitmap)
        return faces.maxByOrNull { it.confidence }
    }

    private fun extractLandmarks(face: Face): FaceLandmarks {
        val points = mutableListOf<Point>()

        // Mevcut landmark'ları doğru isimlerle çıkar
        face.getLandmark(FaceLandmark.LEFT_EYE)?.let {
            points.add(Point(it.position.x, it.position.y))
        }
        face.getLandmark(FaceLandmark.RIGHT_EYE)?.let {
            points.add(Point(it.position.x, it.position.y))
        }
        face.getLandmark(FaceLandmark.NOSE_BASE)?.let {
            points.add(Point(it.position.x, it.position.y))
        }

        // Ağız landmark'ları - doğru isimler
        face.getLandmark(FaceLandmark.MOUTH_LEFT)?.let {
            points.add(Point(it.position.x, it.position.y))
        }
        face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.let {
            points.add(Point(it.position.x, it.position.y))
        }
        face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.let {
            points.add(Point(it.position.x, it.position.y))
        }

        // Yanak landmark'ları
        face.getLandmark(FaceLandmark.LEFT_CHEEK)?.let {
            points.add(Point(it.position.x, it.position.y))
        }
        face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.let {
            points.add(Point(it.position.x, it.position.y))
        }

        return FaceLandmarks(
            points = points.toList(),
            boundingBox = RectF(face.boundingBox),
            confidence = 0.8f
        )
    }

    private fun calculateFaceAngle(face: Face): FaceAngle {
        val yaw = face.headEulerAngleY    // Sağ-sol dönüş
        val pitch = face.headEulerAngleX  // Yukarı-aşağı
        val roll = face.headEulerAngleZ   // Eğim

        return FaceAngle(yaw, pitch, roll)
    }

    private fun calculateConfidence(face: Face): Float {
        // Farklı confidence değerlerini kombine et
        val smileProb = face.smilingProbability ?: 0.5f
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f

        // Weighted average
        return (leftEyeOpen * 0.4f + rightEyeOpen * 0.4f + smileProb * 0.2f).coerceIn(0f, 1f)
    }

    private fun cropFaceBitmap(originalBitmap: Bitmap, boundingBox: android.graphics.Rect): Bitmap {
        return try {
            // Yüz bölgesini %20 büyüterek kırp
            val paddingX = (boundingBox.width() * 0.2f).toInt()
            val paddingY = (boundingBox.height() * 0.2f).toInt()

            val left = maxOf(0, boundingBox.left - paddingX)
            val top = maxOf(0, boundingBox.top - paddingY)
            val right = minOf(originalBitmap.width, boundingBox.right + paddingX)
            val bottom = minOf(originalBitmap.height, boundingBox.bottom + paddingY)

            val width = right - left
            val height = bottom - top

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(originalBitmap, left, top, width, height)
            } else {
                originalBitmap
            }
        } catch (e: Exception) {
            originalBitmap
        }
    }

    fun isQualityGoodEnough(detectedFace: DetectedFace): Boolean {
        return detectedFace.confidence > 0.3f &&
                detectedFace.angle.isValidAngle() &&
                detectedFace.landmarks.points.size >= 5 &&
                detectedFace.boundingBox.width() > 100 &&
                detectedFace.boundingBox.height() > 100
    }

    // MLKit'te mevcut tüm landmark türleri
    fun getAllAvailableLandmarks(): List<Int> {
        return listOf(
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.NOSE_BASE
        )
    }
}