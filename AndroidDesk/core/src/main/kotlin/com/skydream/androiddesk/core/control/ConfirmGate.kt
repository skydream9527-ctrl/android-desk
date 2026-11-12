package com.skydream.androiddesk.core.control

import kotlinx.serialization.Serializable

@Serializable
enum class ConfirmTier(val label: String, val hint: String) {
    AlwaysAsk("每次都询问", "打开应用、改动桌面、发送、删除、付款前都先问你"),
    AskOncePerTask("本任务免确认", "每个任务问一次；发送、付款、删除仍然每次问"),
    NeverAsk("始终免确认", "不再询问。建议只临时使用"),
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

    /** Adding, removing, moving or restyling icons on this desktop. */
    ArrangeDesktop,

    /** Reading this desktop's own layout and the installed-app list (not other apps' screens). */
    ReadDesktop,

    /** The assistant telling the desktop what it is doing, for the pet and status line. */
    ReportStatus,
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
            action == DesktopActionKind.MoveWindow ||
            action == DesktopActionKind.ArrangeDesktop
    }
}
