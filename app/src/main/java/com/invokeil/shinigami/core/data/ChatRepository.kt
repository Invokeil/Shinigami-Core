package com.invokeil.shinigami.core.data

import com.invokeil.shinigami.core.data.db.ConversationDao
import com.invokeil.shinigami.core.data.db.ConversationEntity
import com.invokeil.shinigami.core.data.db.MessageDao
import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/** Conversation + message persistence, honouring incognito & retention. */
@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val prefs: PrefsRepository,
) {
    fun conversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun messages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun currentConversationId(incognito: Boolean): Long {
        if (incognito) {
            // reuse the single temporary conversation while the session lives
            val temp = conversationDao.latestTemporary()
            if (temp != null) return temp.id
            return conversationDao.insert(
                ConversationEntity(title = "Private session", temporary = true),
            )
        }
        val latest = conversations().firstOrNull()
            ?.firstOrNull { !it.temporary }
            ?.takeIf {
                // continue the most recent conversation only when it is fresh (<12h)
                System.currentTimeMillis() - it.updatedAt < 12 * 60 * 60 * 1000
            }
        return latest?.id ?: conversationDao.insert(ConversationEntity())
    }

    suspend fun appendMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        providerId: Long? = null,
        model: String? = null,
    ): MessageEntity? {
        if (content.isBlank()) return null
        val entity = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            providerId = providerId,
            model = model,
        )
        val id = messageDao.insert(entity)
        conversationDao.byId(conversationId)?.let { conv ->
            conversationDao.update(
                conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    title = autoTitle(conv.title, role, content),
                ),
            )
        }
        return entity.copy(id = id)
    }

    private fun autoTitle(currentTitle: String, role: MessageRole, content: String): String =
        if (currentTitle == "New chat" && role == MessageRole.USER) {
            content.trim().take(40).replaceFirstChar { it.uppercase(Locale.ENGLISH) }
        } else {
            currentTitle
        }

    /** Explicitly creates a fresh conversation (New Chat button). */
    suspend fun startNewConversation(): Long = conversationDao.insert(ConversationEntity())

    suspend fun history(conversationId: Long): List<MessageEntity> =
        messageDao.forConversation(conversationId)

    suspend fun rename(conversationId: Long, title: String) =
        conversationDao.rename(conversationId, title)

    suspend fun setPinned(conversationId: Long, pinned: Boolean) =
        conversationDao.setPinned(conversationId, pinned)

    suspend fun deleteConversation(conversationId: Long) {
        messageDao.deleteForConversation(conversationId)
        conversationDao.delete(conversationId)
    }

    /** Applies the retention policy on startup (§58). */
    suspend fun applyRetention() {
        val policy = prefs.historyRetention.firstOrNull() ?: return
        val cutoff = when (policy) {
            HistoryRetention.FOREVER -> return
            HistoryRetention.OFF -> return
            HistoryRetention.DAYS_30 -> System.currentTimeMillis() - 30L * 86_400_000
            HistoryRetention.DAYS_7 -> System.currentTimeMillis() - 7L * 86_400_000
            HistoryRetention.DAYS_1 -> System.currentTimeMillis() - 86_400_000
        }
        conversationDao.deleteOlderThan(cutoff)
    }
}
