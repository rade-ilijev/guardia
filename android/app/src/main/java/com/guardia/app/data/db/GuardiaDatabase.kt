package com.guardia.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonEntity::class,
        FaceSampleEntity::class,
        NegativeFaceEntity::class,
        EventEntity::class,
        IntruderCaptureEntity::class,
        SafeZoneEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class GuardiaDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun eventDao(): EventDao
    abstract fun intruderDao(): IntruderDao
    abstract fun safeZoneDao(): SafeZoneDao
}
