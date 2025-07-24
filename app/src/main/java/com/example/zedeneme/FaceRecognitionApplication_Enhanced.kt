package com.example.zedeneme

import android.app.Application
import com.example.zedeneme.data.FaceDatabase
import com.example.zedeneme.repository.FaceRepository
import com.example.zedeneme.engine.FaceDetectionEngine
import com.example.zedeneme.engine.FeatureExtractionEngine
import com.example.zedeneme.engine.FaceRecognitionEngine
import com.example.zedeneme.engine.EnhancedFaceRecognitionEngine

class FaceRecognitionApplication : Application() {

    val database by lazy { FaceDatabase.getDatabase(this) }
    val repository by lazy { FaceRepository(database.faceDao()) }
    
    // Original engines
    val faceDetectionEngine by lazy { FaceDetectionEngine() }
    val featureExtractionEngine by lazy { FeatureExtractionEngine() }
    val faceRecognitionEngine by lazy { FaceRecognitionEngine(repository, featureExtractionEngine) }
    
    // Enhanced engine with TensorFlow Lite support
    val enhancedFaceRecognitionEngine by lazy { 
        EnhancedFaceRecognitionEngine(
            context = this,
            repository = repository,
            featureExtractor = featureExtractionEngine
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Log engine initialization status
        android.util.Log.d("FaceRecognitionApp", "Initializing face recognition engines...")
        
        val engineStatus = enhancedFaceRecognitionEngine.getEngineStatus()
        android.util.Log.d("FaceRecognitionApp", """
            Engine Status:
            - Active Engine: ${engineStatus.activEngine}
            - TensorFlow Lite Available: ${engineStatus.isTensorFlowLiteAvailable}
            - Feature Size: ${engineStatus.featureSize}
            - Recognition Threshold: ${engineStatus.recognitionThreshold}
            - Expected Accuracy: ${engineStatus.expectedAccuracy}
        """.trimIndent())
    }

    companion object {
        lateinit var instance: FaceRecognitionApplication
            private set
    }
}