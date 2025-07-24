package com.example.zedeneme

import android.app.Application
import com.example.zedeneme.data.FaceDatabase
import com.example.zedeneme.engine.TensorFlowFaceRecognition
import com.example.zedeneme.repository.FaceRepository

class FaceRecognitionApplication : Application() {

    // Repository
    lateinit var repository: FaceRepository
        private set

    // TensorFlow Engine
    lateinit var tensorFlowEngine: TensorFlowFaceRecognition
        private set

    companion object {
        lateinit var instance: FaceRecognitionApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Database ve Repository kurulumu
        val database = FaceDatabase.getDatabase(this)
        repository = FaceRepository(database.faceDao())

        // TensorFlow Lite Engine kurulumu
        tensorFlowEngine = TensorFlowFaceRecognition(this)

        android.util.Log.d("FaceRecognitionApp", "✅ Application başlatıldı - TensorFlow Lite hazır")
    }

    override fun onTerminate() {
        super.onTerminate()

        // TensorFlow Engine'i temizle
        if (::tensorFlowEngine.isInitialized) {
            tensorFlowEngine.release()
            android.util.Log.d("FaceRecognitionApp", "🔒 TensorFlow Lite engine kapatıldı")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.w("FaceRecognitionApp", "⚠️ Düşük bellek uyarısı")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        android.util.Log.d("FaceRecognitionApp", "🧹 Bellek temizleme seviyesi: $level")
    }
}