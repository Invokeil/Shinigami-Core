package com.invokeil.shinigami.feature.routines

import android.content.Context
import com.invokeil.shinigami.core.actions.ActionEngine
import com.invokeil.shinigami.core.actions.ActionResult
import com.invokeil.shinigami.core.actions.ToolCall
import com.invokeil.shinigami.core.data.db.ActionSource
import com.invokeil.shinigami.core.data.db.RoutineDao
import com.invokeil.shinigami.core.data.db.RoutineStepEntity
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Executes routines (v0.2 visual editor) — every step goes through the SAME
 * validated ToolCall → ActionEngine path (MASTER SPEC §24, OVERLAY §54).
 * No routine can bypass policy, confirmation or protected-apps rules.
 */
@Singleton
class RoutineRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routineDao: RoutineDao,
    private val actionEngine: ActionEngine,
    private val confirmationRequester: RoutineConfirmationRequester,
) {
    data class RunProgress(
        val routineId: Long,
        val routineName: String,
        val step: Int,
        val total: Int,
        val currentLabel: String,
        val finished: Boolean = false,
        val error: String? = null,
    )

    private val _progress = MutableStateFlow<RunProgress?>(null)
    val progress: StateFlow<RunProgress?> = _progress

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    suspend fun run(routineId: Long) {
        val routine = routineDao.byId(routineId) ?: return
        if (_running.value) return
        val steps = routineDao.steps(routineId)
        if (steps.isEmpty()) return
        _running.value = true
        try {
            for ((index, step) in steps.withIndex()) {
                _progress.value = RunProgress(
                    routineId = routineId,
                    routineName = routine.name,
                    step = index + 1,
                    total = steps.size,
                    currentLabel = step.toolId,
                )
                val result = executeStep(step)
                if (result is ActionResult.Failure || result is ActionResult.Denied) {
                    val msg = (result as? ActionResult.Failure)?.humanMessage
                        ?: (result as? ActionResult.Denied)?.humanMessage
                        ?: "Step ${index + 1} was not executed."
                    if (!step.continueOnError) {
                        _progress.value = _progress.value?.copy(finished = true, error = msg)
                        return
                    }
                }
            }
            _progress.value = _progress.value?.copy(finished = true)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "routine run failed: ${t.message}")
            _progress.value = _progress.value?.copy(finished = true, error = t.message)
        } finally {
            _running.value = false
        }
    }

    private suspend fun executeStep(step: RoutineStepEntity) = try {
        if (step.toolId == "run_routine") {
            ActionResult.Failure("NESTED_ROUTINE", "Routines can't run other routines (recursion guard).")
        } else {
            val json = JSONObject(step.argumentsJson.ifBlank { "{}" })
            val args = buildMap<String, Any?> {
                json.keys().forEach { k -> put(k, json.opt(k)) }
            }
            actionEngine.execute(context, ToolCall(step.toolId, args), ActionSource.ROUTINE, confirmationRequester)
        }
    } catch (t: Throwable) {
        ActionResult.Failure("BAD_STEP", "A step in this routine is invalid: ${t.message}")
    }

    fun stop() {
        // Emergency stop via the engine cancels queued executions at policy level
        actionEngine.triggerEmergencyStop()
        _progress.value = _progress.value?.copy(finished = true, error = "Stopped.")
        _running.value = false
    }

    private companion object {
        const val TAG = "RoutineRunner"
    }
}

/** Routes routine confirmations to the shared bridge (overlay or app UI). */
@Singleton
class RoutineConfirmationRequester @Inject constructor(
    private val bridge: com.invokeil.shinigami.core.ai.AgentConfirmationBridge,
) : com.invokeil.shinigami.core.actions.ConfirmationRequester {
    override suspend fun requestConfirmation(
        call: com.invokeil.shinigami.core.actions.ValidatedToolCall,
        summary: String,
    ): Boolean {
        val (pending, deferred) = bridge.open(call.def.id, summary)
        return deferred.await()
    }
}
