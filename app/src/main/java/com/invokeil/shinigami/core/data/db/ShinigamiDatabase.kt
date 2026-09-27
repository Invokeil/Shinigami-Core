package com.invokeil.shinigami.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderProfileEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AuditEventEntity::class,
        UsageRecordEntity::class,
        CustomCommandEntity::class,
        MemoryEntryEntity::class,
        ProtectedAppEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ShinigamiDatabase : RoomDatabase() {
    abstract fun providerProfileDao(): ProviderProfileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun auditDao(): AuditDao
    abstract fun usageDao(): UsageDao
    abstract fun customCommandDao(): CustomCommandDao
    abstract fun memoryDao(): MemoryDao
    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun routineDao(): RoutineDao

    companion object {
        const val NAME = "shinigami.db"
    }
}
