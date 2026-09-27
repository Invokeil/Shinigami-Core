package com.invokeil.shinigami.core.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between the (background) agent loop and the (UI) confirmation sheet.
 * The agent parks on [await] while the UI collects a yes/no; emergency stop
 * cancels the parked coroutine through the caller's job.
 */
@Singleton
class AgentConfirmationBridge @Inject constructor() {

    data class Pending(
        val id: Long,
        val summary: String,
        val toolId: String,
    )

    private val mutex = Mutex()
    private var current: Pending? = null
    private var deferred: CompletableDeferred<Boolean>? = null
    private var nextId = 1L

    /** The confirmation the UI should currently display, if any. */
    val pending: Pending? get() = current

    suspend fun open(toolId: String, summary: String): Pair<Pending, CompletableDeferred<Boolean>> =
        mutex.withLock {
            val p = Pending(nextId++, summary, toolId)
            val d = CompletableDeferred<Boolean>()
            current = p
            deferred = d
            p to d
        }

    suspend fun answer(approve: Boolean) {
        mutex.withLock {
            deferred?.complete(approve)
            current = null
            deferred = null
        }
    }

    suspend fun cancel() {
        answer(false)
    }

    /** Suspend until answered or [timeoutMs] passes (safety valve). */
    suspend fun await(p: Pending, d: CompletableDeferred<Boolean>, timeoutMs: Long = 120_000): Boolean =
        withTimeoutOrNull(timeoutMs) { d.await() } ?: false
}
