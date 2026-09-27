package com.invokeil.shinigami.core.actions

import android.content.Context
import com.invokeil.shinigami.core.data.db.AuditDao
import com.invokeil.shinigami.core.data.db.RiskLevel
import com.invokeil.shinigami.core.data.db.ActionSource
import com.invokeil.shinigami.core.data.db.AuditEventEntity
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Result of one executed (or refused) action. */
sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Failure(val code: String, val humanMessage: String) : ActionResult
    data class Denied(val humanMessage: String) : ActionResult
    data class CancelledByUser(val humanMessage: String = "Cancelled.") : ActionResult
}

/** UI implements this to show confirmation sheets and biometric prompts. */
interface ConfirmationRequester {
    /** Returns true when the user approved. Must be cancellable. */
    suspend fun requestConfirmation(call: ValidatedToolCall, summary: String): Boolean
}

/** A no-op requester used by tests and headless routines. */
object AutoConfirmRequester : ConfirmationRequester {
    override suspend fun requestConfirmation(call: ValidatedToolCall, summary: String): Boolean = true
}

/**
 * Executes validated tool calls behind the policy engine and writes an audit
 * trail (MASTER SPEC §22, §69). This is the ONLY path from an AI request to
 * an Android side effect.
 */
@Singleton
class ActionEngine @Inject constructor(
    private val registry: ToolRegistry,
    private val policy: ActionPolicyEngine,
    private val executors: ExecutorDispatcher,
    private val auditDao: AuditDao,
) {
    /** When true, every execution attempt is refused and loops unwind. */
    @Volatile
    var emergencyStop: Boolean = false
        private set

    fun triggerEmergencyStop() {
        emergencyStop = true
        ShiniLog.i(TAG, "EMERGENCY STOP engaged")
    }

    fun clearEmergencyStop() {
        emergencyStop = false
    }

    suspend fun execute(
        context: Context,
        call: ToolCall,
        source: ActionSource,
        confirmer: ConfirmationRequester?,
    ): ActionResult {
        if (emergencyStop) {
            return ActionResult.Denied("Emergency stop is active. Nothing runs until you release it.")
        }

        val validated = when (val v = registry.validate(call)) {
            is ToolRegistry.ValidatedCall.Valid -> ValidatedToolCall(v.def, v.args)
            is ToolRegistry.ValidatedCall.Invalid -> {
                audit(call.tool, source, "DENIED", v.reason, null, RiskLevel.LOW)
                return ActionResult.Failure("INVALID_TOOL", v.reason)
            }
        }

        val decision = try {
            policy.evaluate(context, validated)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            ShiniLog.e(TAG, "policy evaluation failed", t)
            ActionResult.Failure("POLICY_ERROR", "The action could not be validated.")
        }

        when (decision) {
            is ActionPolicyEngine.Decision.Deny -> {
                audit(validated.def.id, source, "DENIED", decision.reason, null, validated.def.risk)
                return ActionResult.Denied(decision.reason)
            }

            is ActionPolicyEngine.Decision.Confirm -> {
                val requester = confirmer
                val approved = requester?.requestConfirmation(validated, decision.summary) ?: false
                if (!approved) {
                    audit(validated.def.id, source, "CANCELLED", "User declined", null, validated.def.risk)
                    return ActionResult.CancelledByUser()
                }
            }

            ActionPolicyEngine.Decision.Allow -> Unit
        }

        if (emergencyStop) {
            return ActionResult.CancelledByUser("Emergency stop engaged before execution.")
        }

        val result = executors.dispatch(context, validated)
        val auditResult = when (result) {
            is ActionResult.Success -> "SUCCESS"
            is ActionResult.Failure -> "FAILED"
            is ActionResult.Denied -> "DENIED"
            is ActionResult.CancelledByUser -> "CANCELLED"
        }
        audit(
            validated.def.id,
            source,
            auditResult,
            result.messageText(),
            targetPackage(validated),
            validated.def.risk,
        )
        return result
    }

    private fun ActionResult.messageText(): String = when (this) {
        is ActionResult.Success -> message
        is ActionResult.Failure -> "$code: $humanMessage"
        is ActionResult.Denied -> humanMessage
        is ActionResult.CancelledByUser -> humanMessage
    }

    private fun targetPackage(call: ValidatedToolCall): String? = when (call.def.id) {
        "open_app", "app_info" -> call.args["app_name"] as? String
        else -> null
    }

    private suspend fun audit(
        action: String,
        source: ActionSource,
        result: String,
        detail: String?,
        pkg: String?,
        risk: RiskLevel,
    ) {
        try {
            auditDao.insert(
                AuditEventEntity(
                    action = action,
                    source = source,
                    result = result,
                    detail = detail?.take(300),
                    targetPackage = pkg,
                    riskLevel = risk,
                ),
            )
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "audit write failed")
        }
    }

    private companion object {
        const val TAG = "ActionEngine"
    }
}

fun ActionResult.summaryLine(): String = when (this) {
    is ActionResult.Success -> message
    is ActionResult.Failure -> humanMessage
    is ActionResult.Denied -> humanMessage
    is ActionResult.CancelledByUser -> humanMessage
}
