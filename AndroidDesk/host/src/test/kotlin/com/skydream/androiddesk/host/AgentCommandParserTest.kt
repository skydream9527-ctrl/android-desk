package com.skydream.androiddesk.host

import com.skydream.androiddesk.core.agent.AgentPresenceState
import com.skydream.androiddesk.core.control.DeskCommand
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconTreatment
import com.skydream.androiddesk.sdk.DeskProtocol.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AgentCommandParserTest {
    private fun ok(method: String, arg: String? = null, args: Map<String, String> = emptyMap()): DeskCommand =
        assertIs<ParsedCommand.Ok>(AgentCommandParser.parse(method, arg, args)).command

    private fun invalid(method: String, arg: String? = null, args: Map<String, String> = emptyMap()) {
        assertIs<ParsedCommand.Invalid>(AgentCommandParser.parse(method, arg, args))
    }

    @Test
    fun primaryArgIsAShorthandForTheMainArgument() {
        assertEquals(DeskCommand.LaunchApp("com.a"), ok(Method.LAUNCH_APP, "com.a"))
        assertEquals(DeskCommand.LaunchApp("com.a"), ok(Method.LAUNCH_APP, null, mapOf("package" to "com.a")))
        assertEquals(DeskCommand.GoToPage(2), ok(Method.GO_TO_PAGE, "2"))
        assertEquals(DeskCommand.AddToDesktop("com.a", 1), ok(Method.ADD_TO_DESKTOP, "com.a", mapOf("page" to "1")))
        assertEquals(DeskCommand.AddToDesktop("com.a", null), ok(Method.ADD_TO_DESKTOP, "com.a"))
    }

    @Test
    fun everyDeclaredMethodExceptHelloParsesWithValidArguments() {
        val args = mapOf("package" to "com.a", "page" to "0", "x" to "1", "y" to "2", "shape" to "circle")
        (Method.all - Method.HELLO).forEach { method ->
            assertIs<ParsedCommand.Ok>(AgentCommandParser.parse(method, null, args), "method $method")
        }
    }

    @Test
    fun missingOrBadArgumentsAreRejectedWithAReason() {
        invalid(Method.LAUNCH_APP)
        invalid(Method.LAUNCH_APP, "   ")
        invalid(Method.GO_TO_PAGE, "-1")
        invalid(Method.GO_TO_PAGE, "two")
        invalid(Method.MOVE_ITEM, "com.a", mapOf("page" to "0", "x" to "1"))
        invalid(Method.ADD_TO_DESKTOP, "com.a", mapOf("page" to "-2"))
        invalid("formatDevice")
    }

    @Test
    fun iconStyleNamesAreCaseInsensitiveAndValidated() {
        assertEquals(
            DeskCommand.SetIconStyle(null, IconShape.Hexagon, IconTreatment.Glass),
            ok(Method.SET_ICON_STYLE, null, mapOf("shape" to "hexagon", "treatment" to "GLASS")),
        )
        assertEquals(
            DeskCommand.SetIconStyle("com.a", null, IconTreatment.Letter),
            ok(Method.SET_ICON_STYLE, "com.a", mapOf("treatment" to "Letter")),
        )
        invalid(Method.SET_ICON_STYLE)
        invalid(Method.SET_ICON_STYLE, null, mapOf("shape" to "star"))
    }

    @Test
    fun anAssistantCannotReportItselfStopped() {
        assertEquals(
            DeskCommand.ReportStatus(AgentPresenceState.Thinking, "在找日历"),
            ok(Method.REPORT_STATUS, "在找日历", mapOf("state" to "thinking")),
        )
        invalid(Method.REPORT_STATUS, "x", mapOf("state" to "stopped"))
        invalid(Method.REPORT_STATUS, "x", mapOf("state" to "sleeping"))
    }
}
