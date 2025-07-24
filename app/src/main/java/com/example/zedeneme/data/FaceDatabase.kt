package com.example.zedeneme.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [FaceProfile::class, FaceFeatures::class],
    version = 1,
    exportSchema = false
)
abstract class FaceDatabase : RoomDatabase() {

    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        fun getDatabase(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_recognition_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}