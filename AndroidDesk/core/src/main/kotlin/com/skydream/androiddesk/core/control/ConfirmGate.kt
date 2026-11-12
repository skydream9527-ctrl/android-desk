package com.skydream.androiddesk.core.control

enum class ConfirmTier {
    AlwaysAsk,
    AskOncePerTask,
    NeverAsk,
}

enum class DesktopActionKind {
    LaunchApp,
    OpenFolder,
    OpenDrawer,
    GoToPage,
    MoveWindow,
    Click,
    LongPress,
    Swipe,
    Type,
    ReadUiTree,
    TakeScreenshot,
    Send,
    Delete,
    Payment,
    EnterPassword,
    EnterOtp,
    OpenSystemSettings,
    Stop,
}

enum class ConfirmDecision {
    Allow,
    AskUser,
    HandBackToUser,
    Deny,
}

data class ConfirmPolicy(
    val tier: ConfirmTier = ConfirmTier.AlwaysAsk,
    val allowPasswordAutomation: Boolean = false,
    val allowOtpAutomation: Boolean = false,
    val allowSystemSettingsAutomation: Boolean = false,
)

class ConfirmGate(
    private val policy: ConfirmPolicy,
    private val askedForTask: MutableSet<String> = mutableSetOf(),
) {
    fun decide(taskId: String, action: DesktopActionKind): ConfirmDecision {
        if (action == DesktopActionKind.Stop) return ConfirmDecision.Allow

        when (action) {
            DesktopActionKind.EnterPassword ->
                return if (policy.allowPasswordAutomation) ConfirmDecision.Allow else ConfirmDecision.HandBackToUser
            DesktopActionKind.EnterOtp ->
                return if (policy.allowOtpAutomation) ConfirmDecision.Allow else ConfirmDecision.HandBackToUser
            DesktopActionKind.OpenSystemSettings ->
                return if (policy.allowSystemSettingsAutomation) ConfirmDecision.Allow else ConfirmDecision.HandBackToUser
            else -> Unit
        }

        val alwaysSensitive = action == DesktopActionKind.Send ||
            action == DesktopActionKind.Delete ||
            action == DesktopActionKind.Payment

        return when (policy.tier) {
            ConfirmTier.AlwaysAsk ->
                if (needsAsk(action, alwaysSensitive)) ConfirmDecision.AskUser else ConfirmDecision.Allow
            ConfirmTier.AskOncePerTask -> when {
                alwaysSensitive -> ConfirmDecision.AskUser
                taskId in askedForTask -> ConfirmDecision.Allow
                needsAsk(action, alwaysSensitive) -> ConfirmDecision.AskUser
                else -> ConfirmDecision.Allow
            }
            ConfirmTier.NeverAsk -> ConfirmDecision.Allow
        }
    }

    fun markTaskConfirmed(taskId: String) {
        askedForTask += taskId
    }

    fun stopTask(taskId: String) {
        askedForTask.remove(taskId)
    }

    private fun needsAsk(action: DesktopActionKind, alwaysSensitive: Boolean): Boolean {
        if (alwaysSensitive) return true
        return action == DesktopActionKind.Click ||
            action == DesktopActionKind.LongPress ||
            action == DesktopActionKind.Swipe ||
            action == DesktopActionKind.Type ||
            action == DesktopActionKind.LaunchApp ||
            action == DesktopActionKind.TakeScreenshot ||
            action == DesktopActionKind.ReadUiTree ||
            action == DesktopActionKind.MoveWindow
    }
}
