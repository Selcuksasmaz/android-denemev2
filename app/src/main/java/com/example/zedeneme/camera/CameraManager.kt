package com.example.zedeneme.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "CameraManager"
    }

    suspend fun setupCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onImageAnalyzed: (Bitmap) -> Unit
    ) {
        try {
            Log.d(TAG, "Camera setup başlıyor...")

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()

            // Preview setup
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image analyzer setup
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, onImageAnalyzed)
                    }
                }

            // Camera selector (front camera for face recognition)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Unbind use cases before rebinding
            cameraProvider?.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            Log.d(TAG, "Camera başarıyla kuruldu")

        } catch (exc: Exception) {
            Log.e(TAG, "Camera setup hatası", exc)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy, onImageAnalyzed: (Bitmap) -> Unit) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            bitmap?.let {
                // Front camera için görüntüyü çevir
                val flippedBitmap = flipBitmap(it)
                onImageAnalyzed(flippedBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image conversion hatası", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
            val imageBytes = out.toByteArray()

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion hatası", e)
            null
        }
    }

    private fun flipBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(-1.0f, 1.0f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun startCamera() {
        Log.d(TAG, "Camera başlatıldı")
    }

    fun stopCamera() {
        Log.d(TAG, "Camera durduruldu")
        cameraProvider?.unbindAll()
    }

    fun toggleFlash(): Boolean {
        return try {
            camera?.let { cam ->
                val currentFlashMode = cam.cameraInfo.torchState.value
                cam.cameraControl.enableTorch(currentFlashMode != TorchState.ON)
                Log.d(TAG, "Flash toggle: ${currentFlashMode != TorchState.ON}")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Flash toggle hatası", e)
            false
        }
    }

    fun release() {
        Log.d(TAG, "Camera manager temizleniyor")
        cameraExecutor.shutdown()
        stopCamera()
    }
}