package com.example.zedeneme

import android.app.Application
import com.example.zedeneme.data.FaceDatabase
import com.example.zedeneme.repository.FaceRepository

class FaceRecognitionApplication : Application() {

    val database by lazy { FaceDatabase.getDatabase(this) }
    val repository by lazy { FaceRepository(database.faceDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FaceRecognitionApplication
            private set
    }
}