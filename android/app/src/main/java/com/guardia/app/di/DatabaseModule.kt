package com.guardia.app.di

import android.content.Context
import androidx.room.Room
import com.guardia.app.BuildConfig
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guardia.app.data.db.EventDao
import com.guardia.app.data.db.GuardiaDatabase
import com.guardia.app.data.db.IntruderDao
import com.guardia.app.data.db.PersonDao
import com.guardia.app.data.db.SafeZoneDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN lastSeenAt INTEGER")
            db.execSQL("ALTER TABLE people ADD COLUMN recognitionCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE people ADD COLUMN confidenceSum REAL NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS safe_zones (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "latitude REAL NOT NULL, " +
                    "longitude REAL NOT NULL, " +
                    "radiusMeters INTEGER NOT NULL, " +
                    "guardEnabled INTEGER NOT NULL, " +
                    "responsiveness INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN lockOnNoFace INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN useDefault INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN customIntervalSeconds INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN firstCheckOnUnlock INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN checkRamp TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN shakeToCheck INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE face_samples ADD COLUMN photoPath TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS negative_faces (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "personId TEXT, " +
                    "embedding BLOB NOT NULL, " +
                    "photoPath TEXT, " +
                    "createdAt INTEGER NOT NULL)"
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing rows keep version 0 (old pipeline); the recognizer ignores them and the UI
            // prompts re-enrollment so improved preprocessing never causes an owner lockout.
            db.execSQL("ALTER TABLE face_samples ADD COLUMN modelVersion INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE negative_faces ADD COLUMN modelVersion INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE people ADD COLUMN gender TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GuardiaDatabase =
        Room.databaseBuilder(context, GuardiaDatabase::class.java, "guardia.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .apply {
                // In release we never silently wipe the user's enrolled faces and evidence on a
                // schema mismatch — every version bump must ship an explicit migration above.
                // Debug keeps destructive fallback so throwaway schema experiments don't crash.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
            }
            .build()

    @Provides
    fun providePersonDao(db: GuardiaDatabase): PersonDao = db.personDao()

    @Provides
    fun provideEventDao(db: GuardiaDatabase): EventDao = db.eventDao()

    @Provides
    fun provideIntruderDao(db: GuardiaDatabase): IntruderDao = db.intruderDao()

    @Provides
    fun provideSafeZoneDao(db: GuardiaDatabase): SafeZoneDao = db.safeZoneDao()
}
