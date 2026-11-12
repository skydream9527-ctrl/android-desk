package com.skydream.androiddesk.core.control

import com.skydream.androiddesk.core.agent.AgentPresenceState
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconTreatment
import kotlinx.serialization.Serializable

/**
 * Everything an assistant can ask this desktop to do to itself. These go to the desktop's
 * own state machine, never through accessibility: the desktop does not "tap itself".
 * Acting inside other apps (click / type) is a separate surface that waits for W0-T4.
 */
sealed interface DeskCommand {
    val kind: DesktopActionKind

    data class LaunchApp(val packageName: String) : DeskCommand {
        override val kind get() = DesktopActionKind.LaunchApp
    }

    data class GoToPage(val pageIndex: Int) : DeskCommand {
        override val kind get() = DesktopActionKind.GoToPage
    }

    data object OpenDrawer : DeskCommand {
        override val kind get() = DesktopActionKind.OpenDrawer
    }

    data object CloseDrawer : DeskCommand {
        override val kind get() = DesktopActionKind.OpenDrawer
    }

    data class AddToDesktop(val packageName: String, val pageIndex: Int? = null) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    data class RemoveFromDesktop(val packageName: String) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    data class MoveItem(val packageName: String, val pageIndex: Int, val cellX: Int, val cellY: Int) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    data class PinToDock(val packageName: String) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    data class UnpinFromDock(val packageName: String) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    /** [packageName] null restyles every icon; otherwise only that app. */
    data class SetIconStyle(
        val packageName: String?,
        val shape: IconShape?,
        val treatment: IconTreatment?,
    ) : DeskCommand {
        override val kind get() = DesktopActionKind.ArrangeDesktop
    }

    data object ReadDesktop : DeskCommand {
        override val kind get() = DesktopActionKind.ReadDesktop
    }

    data object ListApps : DeskCommand {
        override val kind get() = DesktopActionKind.ReadDesktop
    }

    data class ReportStatus(val state: AgentPresenceState, val text: String?) : DeskCommand {
        override val kind get() = DesktopActionKind.ReportStatus
    }

    /** The assistant ending its own task. A user stop is not a command; it is the desktop's own switch. */
    data object EndTask : DeskCommand {
        override val kind get() = DesktopActionKind.Stop
    }
}

/**
 * Who may ask this desktop to act, and how often the user is asked. Lives on the desktop
 * side only; an assistant cannot change its own tier.
 */
@Serializable
data class AgentAccess(
    /** Packages the user explicitly allowed to see the layout and request desktop actions. */
    val allowedPackages: Set<String> = emptySet(),
    val tier: ConfirmTier = ConfirmTier.AlwaysAsk,
    /** Debug builds only: treat `adb shell` as an assistant, for trying the protocol by hand. */
    val allowAdbShell: Boolean = false,
) {
    fun isAllowed(callerPackage: String?, debuggable: Boolean): Boolean = when {
        callerPackage.isNullOrBlank() -> false
        callerPackage == ADB_SHELL_PACKAGE -> debuggable && allowAdbShell
        else -> callerPackage in allowedPackages
    }

    companion object {
        const val ADB_SHELL_PACKAGE = "com.android.shell"
    }
}
