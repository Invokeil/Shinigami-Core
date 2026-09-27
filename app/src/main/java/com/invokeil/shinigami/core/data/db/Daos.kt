package com.invokeil.shinigami.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderProfileDao {
    @Query("SELECT * FROM provider_profiles ORDER BY isDefault DESC, name")
    fun observeAll(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles ORDER BY isDefault DESC, name")
    suspend fun all(): List<ProviderProfileEntity>

    @Query("SELECT * FROM provider_profiles WHERE id = :id")
    suspend fun byId(id: Long): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultProfile(): ProviderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProviderProfileEntity): Long

    @Update
    suspend fun update(profile: ProviderProfileEntity)

    @Delete
    suspend fun delete(profile: ProviderProfileEntity)

    @Query("UPDATE provider_profiles SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM provider_profiles")
    suspend fun count(): Int
}

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations WHERE temporary = 0
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE temporary = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestTemporary(): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations WHERE temporary = 1")
    suspend fun deleteTemporary()

    @Query("DELETE FROM conversations WHERE updatedAt < :cutoff AND pinned = 0")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE temporary = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun search(q: String): Flow<List<ConversationEntity>>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun forConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity): Long

    @Query("DELETE FROM audit_events")
    suspend fun clear()

    @Query("DELETE FROM audit_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun count(): Int
}

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(record: UsageRecordEntity)

    @Query(
        """
        SELECT SUM(inputTokens) FROM usage_records
        WHERE providerId = :providerId AND timestamp >= :since
        """,
    )
    suspend fun inputTokensSince(providerId: Long, since: Long): Int?

    @Query(
        """
        SELECT SUM(outputTokens) FROM usage_records
        WHERE providerId = :providerId AND timestamp >= :since
        """,
    )
    suspend fun outputTokensSince(providerId: Long, since: Long): Int?

    @Query("SELECT COUNT(*) FROM usage_records WHERE providerId = :providerId AND timestamp >= :since")
    suspend fun requestCount(providerId: Long, since: Long): Int
}

@Dao
interface CustomCommandDao {
    @Query("SELECT * FROM custom_commands ORDER BY phrase")
    fun observeAll(): Flow<List<CustomCommandEntity>>

    @Query("SELECT * FROM custom_commands ORDER BY phrase")
    suspend fun all(): List<CustomCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: CustomCommandEntity): Long

    @Delete
    suspend fun delete(command: CustomCommandEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntryEntity): Long

    @Query("SELECT * FROM memory_entries WHERE category = :category AND key = :key LIMIT 1")
    suspend fun find(category: String, key: String): MemoryEntryEntity?

    @Delete
    suspend fun delete(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries")
    suspend fun clear()
}

@Dao
interface ProtectedAppDao {
    @Query("SELECT * FROM protected_apps ORDER BY label")
    fun observeAll(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT * FROM protected_apps")
    suspend fun all(): List<ProtectedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: ProtectedAppEntity)

    @Query("DELETE FROM protected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM protected_apps WHERE packageName = :packageName)")
    suspend fun isProtected(packageName: String): Boolean
}
