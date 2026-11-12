package com.skydream.androiddesk.app.agent

import android.os.Looper
import com.skydream.androiddesk.app.DeskController
import com.skydream.androiddesk.app.Prepared
import com.skydream.androiddesk.app.Screen
import com.skydream.androiddesk.core.agent.AgentPresenceState
import com.skydream.androiddesk.core.control.ConfirmDecision
import com.skydream.androiddesk.core.control.ConfirmGate
import com.skydream.androiddesk.core.control.ConfirmPolicy
import com.skydream.androiddesk.core.control.ConfirmTier
import com.skydream.androiddesk.core.control.DeskCommand
import com.skydream.androiddesk.core.control.DesktopActionKind
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.host.AgentCommandParser
import com.skydream.androiddesk.host.ParsedCommand
import com.skydream.androiddesk.sdk.DeskProtocol
import com.skydream.androiddesk.sdk.DeskProtocol.Arg
import com.skydream.androiddesk.sdk.DeskProtocol.Method
import com.skydream.androiddesk.sdk.DeskProtocol.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class AgentReply(val status: String, val message: String, val data: String? = null)

data class PendingConfirm(val id: Long, val agentLabel: String, val description: String)

enum class AgentOutcome { Done, Denied, Stopped, Refused }

data class AgentLogEntry(
    val id: Long,
    val timeMillis: Long,
    val agentLabel: String,
    val text: String,
    val outcome: AgentOutcome,
)

data class AgentUiState(
    val presence: AgentPresenceState = AgentPresenceState.Idle,
    /** Whoever asked for something most recently. */
    val agentLabel: String? = null,
    val agentPackage: String? = null,
    /** One line for the status bar above the dock: what the assistant says it is doing or just did. */
    val statusText: String? = null,
    val pending: PendingConfirm? = null,
    /** Set by the user's stop (or by the screen turning off). Only the user clears it. */
    val stopped: Boolean = false,
    val log: List<AgentLogEntry> = emptyList(),
) {
    /** True while there is something the user might want to stop. */
    val busy: Boolean
        get() = pending != null ||
            presence == AgentPresenceState.Thinking || presence == AgentPresenceState.Acting
}

private enum class Answer { Allowed, Denied, Stopped, TimedOut }

/**
 * The single place assistant requests pass through: permission, the confirm gate, the user's
 * answer, stop, and the log. The desktop's own buttons do not go through here; they call the
 * controller directly, which is how the user and an assistant act on one desktop together.
 */
class AgentHub(private val controller: DeskController) {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    /** Whether the desktop is on screen. Set by the activity. */
    @Volatile
    var deskVisible: Boolean = false

    private val gateLock = Any()
    private var gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AlwaysAsk))
    private var gateTier = ConfirmTier.AlwaysAsk

    private val askLock = Any()
    private val waiting = AtomicReference<CompletableFuture<Answer>?>(null)
    private val ids = AtomicLong()
    private val demoRunning = AtomicBoolean(false)
    private var settleJob: Job? = null

    // ---- Entry point for other apps ----

    /** Called on a binder thread by the provider. Blocks while the user is being asked. */
    fun handle(callerPackage: String?, method: String, arg: String?, args: Map<String, String>): AgentReply {
        val allowed = controller.state.value.settings.agentAccess.isAllowed(callerPackage, controller.debuggable)
        if (method == Method.HELLO) {
            val data = JSONObject()
                .put("version", DeskProtocol.VERSION)
                .put("allowed", allowed)
                .put("deskVisible", deskVisible)
                .toString()
            return AgentReply(Status.OK, if (allowed) "已允许操作桌面" else "用户还没有允许这个应用操作桌面", data)
        }
        if (!allowed || callerPackage == null) {
            return AgentReply(Status.NOT_ALLOWED, "用户还没有允许这个应用操作桌面。请用户在「桌面设置 › 助手与操控」里打开。")
        }
        return dispatch(callerPackage, controller.agentLabel(callerPackage), method, arg, args)
    }

    private fun dispatch(
        caller: String,
        label: String,
        method: String,
        arg: String?,
        args: Map<String, String>,
    ): AgentReply {
        val command = when (val parsed = AgentCommandParser.parse(method, arg, args)) {
            is ParsedCommand.Invalid -> return AgentReply(Status.INVALID, parsed.reason)
            is ParsedCommand.Ok -> parsed.command
        }
        val taskKey = caller + ":" + (args[Arg.TASK]?.takeIf { it.isNotBlank() } ?: "default")

        if (command is DeskCommand.EndTask) {
            synchronized(gateLock) { gate.stopTask(taskKey) }
            _state.update {
                if (it.stopped) it else it.copy(presence = AgentPresenceState.Idle, statusText = null)
            }
            return AgentReply(Status.OK, "任务已结束")
        }
        if (_state.value.stopped) return stoppedReply()

        if (command is DeskCommand.ReportStatus) {
            _state.update {
                it.copy(presence = command.state, agentLabel = label, agentPackage = caller, statusText = command.text)
            }
            settleLater(20_000)
            return AgentReply(Status.OK, "已显示")
        }

        val readOnly = command.kind == DesktopActionKind.ReadDesktop
        if (!readOnly && !deskVisible) {
            return AgentReply(Status.DESK_NOT_VISIBLE, "桌面不在前台，用户看不到这次操作。等用户回到桌面再试。")
        }

        val prepared = when (val p = onMain { controller.prepare(command) }) {
            is Prepared.Refused -> {
                log(label, p.message, AgentOutcome.Refused)
                return AgentReply(Status.INVALID, p.message)
            }
            is Prepared.Ready -> p
        }

        val tier = controller.state.value.settings.agentAccess.tier
        val decision = synchronized(gateLock) {
            if (tier != gateTier) {
                // A changed tier starts clean: nothing confirmed under the old one carries over.
                gate = ConfirmGate(ConfirmPolicy(tier))
                gateTier = tier
            }
            gate.decide(taskKey, command.kind)
        }
        when (decision) {
            ConfirmDecision.Allow -> Unit
            ConfirmDecision.AskUser -> when (ask(caller, label, prepared.description)) {
                Answer.Allowed -> synchronized(gateLock) { gate.markTaskConfirmed(taskKey) }
                Answer.Denied -> {
                    log(label, prepared.description, AgentOutcome.Denied)
                    return AgentReply(Status.DENIED, "用户没有允许这次操作。")
                }
                Answer.Stopped -> return stoppedReply()
                Answer.TimedOut -> return AgentReply(Status.TIMEOUT, "用户没有在限定时间内回应。")
            }
            ConfirmDecision.HandBackToUser ->
                return AgentReply(Status.NEEDS_USER, "需要你来操作：这一步桌面不替助手做。")
            ConfirmDecision.Deny -> return AgentReply(Status.DENIED, "桌面不允许这个操作。")
        }
        // The user may have pressed stop while this request was waiting its turn.
        if (_state.value.stopped) return stoppedReply()

        val reply = onMain { prepared.run() }
        if (!readOnly) {
            val done = reply.status == Status.OK
            log(label, reply.message, if (done) AgentOutcome.Done else AgentOutcome.Refused)
            _state.update {
                it.copy(
                    presence = AgentPresenceState.Acting,
                    agentLabel = label,
                    agentPackage = caller,
                    statusText = reply.message,
                )
            }
            settleLater(3_500)
        }
        return reply
    }

    private fun stoppedReply() = AgentReply(Status.STOPPED, "用户已停止助手操作。要继续，需要用户在桌面上点「继续」。")

    private fun <T> onMain(block: () -> T): T =
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else runBlocking(Dispatchers.Main.immediate) { block() }

    /** Shows the confirm card and waits for the user. One question at a time; others queue here. */
    private fun ask(caller: String, label: String, description: String): Answer {
        // Waiting here on the main thread would freeze the very UI that has to answer.
        if (Looper.myLooper() == Looper.getMainLooper()) return Answer.Denied
        synchronized(askLock) {
            if (_state.value.stopped) return Answer.Stopped
            val future = CompletableFuture<Answer>()
            val id = ids.incrementAndGet()
            waiting.set(future)
            _state.update {
                it.copy(
                    pending = PendingConfirm(id, label, description),
                    presence = AgentPresenceState.NeedsUser,
                    agentLabel = label,
                    agentPackage = caller,
                )
            }
            val answer = try {
                future.get(DeskProtocol.CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                Answer.TimedOut
            } catch (_: Exception) {
                Answer.Stopped
            }
            waiting.compareAndSet(future, null)
            _state.update {
                if (it.pending?.id != id) it
                else it.copy(
                    pending = null,
                    presence = if (it.stopped) AgentPresenceState.Stopped else AgentPresenceState.Idle,
                )
            }
            return answer
        }
    }

    // ---- The user's side ----

    fun answer(id: Long, allow: Boolean) {
        if (_state.value.pending?.id != id) return
        waiting.get()?.complete(if (allow) Answer.Allowed else Answer.Denied)
    }

    /** Takes effect at once: the open question is withdrawn and nothing further is accepted. */
    fun userStop(reason: String = "已停止。助手不会自己恢复。") {
        val wasBusy = _state.value.busy
        _state.update {
            it.copy(stopped = true, presence = AgentPresenceState.Stopped, statusText = reason, pending = null)
        }
        waiting.getAndSet(null)?.complete(Answer.Stopped)
        synchronized(gateLock) { gate = ConfirmGate(ConfirmPolicy(gateTier)) }
        settleJob?.cancel()
        if (wasBusy) log(_state.value.agentLabel ?: "助手", "被你停止", AgentOutcome.Stopped)
    }

    fun userResume() {
        _state.update { it.copy(stopped = false, presence = AgentPresenceState.Idle, statusText = null) }
    }

    /** Nothing keeps tapping while the screen is off. */
    fun onScreenOff() {
        if (_state.value.busy) userStop("已息屏，助手任务已停止。")
    }

    private fun settleLater(afterMillis: Long) {
        settleJob?.cancel()
        settleJob = controller.scope.launch {
            delay(afterMillis)
            _state.update {
                if (it.stopped || it.pending != null) it
                else it.copy(presence = AgentPresenceState.Idle, statusText = null)
            }
        }
    }

    private fun log(label: String, text: String, outcome: AgentOutcome) {
        if (text.isBlank()) return
        val entry = AgentLogEntry(ids.incrementAndGet(), System.currentTimeMillis(), label, text, outcome)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(40)) }
    }

    // ---- Built-in demo ----

    /**
     * A fixed script that sends the same requests a real assistant would, through the same
     * gate. It is not an assistant: no model, no network. It exists so the shared-control
     * flow can be seen without installing anything else.
     */
    fun runDemo() {
        if (!demoRunning.compareAndSet(false, true)) return
        controller.openScreen(Screen.Desktop)
        thread(name = "desk-demo") {
            try {
                fun step(method: String, arg: String? = null, args: Map<String, String> = emptyMap()): Boolean {
                    val reply = dispatch(DEMO_CALLER, DEMO_LABEL, method, arg, args + (Arg.TASK to "demo"))
                    Thread.sleep(1100)
                    return reply.status != Status.STOPPED
                }
                Thread.sleep(500)
                if (!step(Method.REPORT_STATUS, "先看看桌面上有什么", mapOf(Arg.STATE to "thinking"))) return@thread
                if (controller.state.value.layout.pageCount > 1) {
                    if (!step(Method.GO_TO_PAGE, "1")) return@thread
                    if (!step(Method.GO_TO_PAGE, "0")) return@thread
                }
                if (!step(Method.OPEN_DRAWER)) return@thread
                if (!step(Method.CLOSE_DRAWER)) return@thread
                val current = controller.state.value.iconTheme.global.shape
                val next = IconShape.entries[(current.ordinal + 1) % IconShape.entries.size]
                if (!step(Method.SET_ICON_STYLE, null, mapOf(Arg.SHAPE to next.name))) return@thread
                step(Method.END_TASK)
            } finally {
                demoRunning.set(false)
            }
        }
    }

    private companion object {
        const val DEMO_CALLER = "com.skydream.androiddesk.demo"
        const val DEMO_LABEL = "演示脚本"
    }
}
