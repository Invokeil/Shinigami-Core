package com.invokeil.shinigami.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.invokeil.shinigami.core.data.db.AuditDao
import com.invokeil.shinigami.core.data.db.ConversationDao
import com.invokeil.shinigami.core.data.db.CustomCommandDao
import com.invokeil.shinigami.core.data.db.MemoryDao
import com.invokeil.shinigami.core.data.db.MessageDao
import com.invokeil.shinigami.core.data.db.ProtectedAppDao
import com.invokeil.shinigami.core.data.db.ProviderProfileDao
import com.invokeil.shinigami.core.data.db.RoutineDao
import com.invokeil.shinigami.core.data.db.ShinigamiDatabase
import com.invokeil.shinigami.core.data.db.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShinigamiDatabase =
        Room.databaseBuilder(context, ShinigamiDatabase::class.java, ShinigamiDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    /** v0.2.0: routines + routine_steps (additive — never destructive). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS routines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    triggerType TEXT NOT NULL,
                    triggerPhrase TEXT,
                    triggerHour INTEGER,
                    triggerMinute INTEGER,
                    enabled INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""",
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS routine_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routineId INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    toolId TEXT NOT NULL,
                    argumentsJson TEXT NOT NULL,
                    continueOnError INTEGER NOT NULL
                )""",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_routine_steps_routineId ON routine_steps(routineId)",
            )
        }
    }

    @Provides fun providerProfileDao(db: ShinigamiDatabase): ProviderProfileDao = db.providerProfileDao()
    @Provides fun conversationDao(db: ShinigamiDatabase): ConversationDao = db.conversationDao()
    @Provides fun messageDao(db: ShinigamiDatabase): MessageDao = db.messageDao()
    @Provides fun auditDao(db: ShinigamiDatabase): AuditDao = db.auditDao()
    @Provides fun usageDao(db: ShinigamiDatabase): UsageDao = db.usageDao()
    @Provides fun customCommandDao(db: ShinigamiDatabase): CustomCommandDao = db.customCommandDao()
    @Provides fun memoryDao(db: ShinigamiDatabase): MemoryDao = db.memoryDao()
    @Provides fun protectedAppDao(db: ShinigamiDatabase): ProtectedAppDao = db.protectedAppDao()
    @Provides fun routineDao(db: ShinigamiDatabase): RoutineDao = db.routineDao()
}
