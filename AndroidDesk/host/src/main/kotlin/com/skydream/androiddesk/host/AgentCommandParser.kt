package com.skydream.androiddesk.host

import com.skydream.androiddesk.core.agent.AgentPresenceState
import com.skydream.androiddesk.core.control.DeskCommand
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconTreatment
import com.skydream.androiddesk.sdk.DeskProtocol.Arg
import com.skydream.androiddesk.sdk.DeskProtocol.Method

sealed interface ParsedCommand {
    data class Ok(val command: DeskCommand) : ParsedCommand
    data class Invalid(val reason: String) : ParsedCommand
}

/** Turns one protocol call into a [DeskCommand]. Pure, so the wire format is unit-tested. */
object AgentCommandParser {
    fun parse(method: String, primaryArg: String?, args: Map<String, String> = emptyMap()): ParsedCommand {
        fun text(key: String, primary: Boolean = false): String? =
            (args[key] ?: if (primary) primaryArg else null)?.trim()?.takeIf { it.isNotEmpty() }

        fun int(key: String, primary: Boolean = false): Int? = text(key, primary)?.toIntOrNull()

        fun withPackage(build: (String) -> DeskCommand): ParsedCommand {
            val pkg = text(Arg.PACKAGE, primary = true) ?: return ParsedCommand.Invalid("缺少参数 package")
            return ParsedCommand.Ok(build(pkg))
        }

        return when (method) {
            Method.READ_DESKTOP -> ParsedCommand.Ok(DeskCommand.ReadDesktop)
            Method.LIST_APPS -> ParsedCommand.Ok(DeskCommand.ListApps)
            Method.OPEN_DRAWER -> ParsedCommand.Ok(DeskCommand.OpenDrawer)
            Method.CLOSE_DRAWER -> ParsedCommand.Ok(DeskCommand.CloseDrawer)
            Method.END_TASK -> ParsedCommand.Ok(DeskCommand.EndTask)
            Method.GO_TO_PAGE -> {
                val page = int(Arg.PAGE, primary = true)
                if (page == null || page < 0) ParsedCommand.Invalid("page 必须是从 0 开始的页码")
                else ParsedCommand.Ok(DeskCommand.GoToPage(page))
            }
            Method.LAUNCH_APP -> withPackage { DeskCommand.LaunchApp(it) }
            Method.ADD_TO_DESKTOP -> {
                val page = int(Arg.PAGE)
                if (page != null && page < 0) ParsedCommand.Invalid("page 不能是负数")
                else withPackage { DeskCommand.AddToDesktop(it, page) }
            }
            Method.REMOVE_FROM_DESKTOP -> withPackage { DeskCommand.RemoveFromDesktop(it) }
            Method.PIN_TO_DOCK -> withPackage { DeskCommand.PinToDock(it) }
            Method.UNPIN_FROM_DOCK -> withPackage { DeskCommand.UnpinFromDock(it) }
            Method.MOVE_ITEM -> {
                val page = int(Arg.PAGE)
                val x = int(Arg.X)
                val y = int(Arg.Y)
                if (page == null || x == null || y == null || page < 0 || x < 0 || y < 0) {
                    ParsedCommand.Invalid("moveItem 需要 page、x、y，且都不能是负数")
                } else {
                    withPackage { DeskCommand.MoveItem(it, page, x, y) }
                }
            }
            Method.SET_ICON_STYLE -> {
                val shapeText = text(Arg.SHAPE)
                val treatmentText = text(Arg.TREATMENT)
                val shape = shapeText?.let { name -> IconShape.entries.find { it.name.equals(name, ignoreCase = true) } }
                val treatment = treatmentText?.let { name ->
                    IconTreatment.entries.find { it.name.equals(name, ignoreCase = true) }
                }
                when {
                    shapeText == null && treatmentText == null ->
                        ParsedCommand.Invalid("setIconStyle 至少需要 shape 或 treatment")
                    shapeText != null && shape == null ->
                        ParsedCommand.Invalid("未知的 shape：$shapeText。可用：${IconShape.entries.joinToString { it.name }}")
                    treatmentText != null && treatment == null ->
                        ParsedCommand.Invalid(
                            "未知的 treatment：$treatmentText。可用：${IconTreatment.entries.joinToString { it.name }}",
                        )
                    else -> ParsedCommand.Ok(
                        DeskCommand.SetIconStyle(text(Arg.PACKAGE, primary = true), shape, treatment),
                    )
                }
            }
            Method.REPORT_STATUS -> {
                val stateText = text(Arg.STATE) ?: AgentPresenceState.Acting.name
                val state = AgentPresenceState.entries.find { it.name.equals(stateText, ignoreCase = true) }
                when (state) {
                    null -> ParsedCommand.Invalid("未知的 state：$stateText")
                    // Only the user can stop or resume; an assistant cannot claim that state.
                    AgentPresenceState.Stopped -> ParsedCommand.Invalid("stopped 只能由用户触发，结束任务请用 endTask")
                    else -> ParsedCommand.Ok(DeskCommand.ReportStatus(state, text(Arg.TEXT, primary = true)?.take(80)))
                }
            }
            else -> ParsedCommand.Invalid("未知的方法：$method")
        }
    }
}
