package com.invokeil.shinigami.core.ai

import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole

/**
 * Context budgeting (MASTER SPEC §77): never send unbounded history.
 * Strategy: system prompt + last N messages within a character budget,
 * always keeping the newest user message intact.
 */
object ContextBudgeter {

    private const val DEFAULT_CHAR_BUDGET = 12_000 // ≈3k tokens
    private const val MAX_MESSAGES = 24

    fun select(history: List<MessageEntity>, charBudget: Int = DEFAULT_CHAR_BUDGET): List<MessageEntity> {
        if (history.isEmpty()) return history
        // Always keep the most recent message; walk backwards accumulating.
        val kept = ArrayList<MessageEntity>(MAX_MESSAGES)
        var used = 0
        for (msg in history.reversed()) {
            if (kept.size >= MAX_MESSAGES) break
            val cost = msg.content.length
            if (used + cost > charBudget && kept.isNotEmpty()) break
            kept.add(msg)
            used += cost
        }
        kept.reverse()
        // Conversation must not begin with an orphan assistant/tool message.
        while (kept.isNotEmpty() && kept.first().role != MessageRole.USER) {
            kept.removeAt(0)
        }
        return kept
    }
}
