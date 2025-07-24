package com.example.zedeneme

import android.app.Application
import com.example.zedeneme.database.FaceDatabase
import com.example.zedeneme.repository.FaceRepository
import com.example.zedeneme.engine.TensorFlowFaceRecognition

class FaceRecognitionApplication : Application() {

    lateinit var repository: FaceRepository
    lateinit var tensorFlowEngine: TensorFlowFaceRecognition

    companion object {
        lateinit var instance: FaceRecognitionApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Database initialization
        val database = FaceDatabase.getDatabase(this)
        repository = FaceRepository(database.faceDao())

        // TensorFlow Lite Engine initialization
        tensorFlowEngine = TensorFlowFaceRecognition(this)

        android.util.Log.d("FaceRecognitionApp", "✅ Application initialized with TensorFlow Lite")
    }

    override fun onTerminate() {
        super.onTerminate()
        // TensorFlow resources cleanup
        if (::tensorFlowEngine.isInitialized) {
            tensorFlowEngine.release()
            android.util.Log.d("FaceRecognitionApp", "🔒 TensorFlow resources released")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Memory pressure handling
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                android.util.Log.w("FaceRecognitionApp", "⚠️ Memory pressure detected, cleaning up...")
                // Optionally restart TensorFlow engine if needed
            }
        }
    }
}