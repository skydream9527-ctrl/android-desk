package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.control.AgentAccess
import com.skydream.androiddesk.core.control.ConfirmTier
import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconStyleOverride
import com.skydream.androiddesk.core.icon.IconTheme
import com.skydream.androiddesk.core.icon.IconTreatment
import com.skydream.androiddesk.core.layout.DesktopLayout
import com.skydream.androiddesk.core.layout.GridDensity
import com.skydream.androiddesk.core.layout.GridSpec
import com.skydream.androiddesk.core.state.DeskSettings
import com.skydream.androiddesk.core.state.DeskSnapshot
import com.skydream.androiddesk.core.state.DeskSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeskSnapshotCodecTest {
    private val snapshot = DeskSnapshot(
        layout = DesktopLayout.empty(GridSpec(4, 6, 4))
            .placeApp("com.android.settings", "com.android.settings.Settings", "设置")
            .placeApp("com.example.notes", null, "Notes")
            .pinAppToDock("com.example.notes"),
        iconTheme = IconTheme(IconStyle(IconShape.Flower, IconTreatment.Gradient, 115, showLabel = false))
            .withOverride("com.example.notes", IconStyleOverride(treatment = IconTreatment.Letter)),
        settings = DeskSettings(
            density = GridDensity.Compact,
            hiddenApps = setOf("com.example.hidden/Main"),
            agentAccess = AgentAccess(setOf("io.github.ccch1mneyyy.dsh_app"), ConfirmTier.AskOncePerTask),
            systemLauncher = "com.android.launcher3/.Launcher",
            seeded = true,
        ),
    )

    @Test
    fun roundTripsEverything() {
        assertEquals(snapshot, DeskSnapshotCodec.decode(DeskSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun corruptOrEmptyInputIsNullNotACrash() {
        assertNull(DeskSnapshotCodec.decode(null))
        assertNull(DeskSnapshotCodec.decode(""))
        assertNull(DeskSnapshotCodec.decode("{not json"))
        assertNull(DeskSnapshotCodec.decode("""{"layout":{"grid":{"columns":0,"rows":4}}}"""))
        // A negative cell violates the model's own rules and must not load.
        val bad = DeskSnapshotCodec.encode(snapshot).replace("\"cellX\":0", "\"cellX\":-1")
        assertNull(DeskSnapshotCodec.decode(bad))
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() {
        val text = DeskSnapshotCodec.encode(snapshot).replaceFirst("{", "{\"futureField\":123,")
        assertEquals(snapshot, DeskSnapshotCodec.decode(text))
    }

    @Test
    fun accessDefaultsAreTheStrictOnes() {
        val access = AgentAccess()
        assertEquals(ConfirmTier.AlwaysAsk, access.tier)
        assertFalse(access.isAllowed("com.example.agent", debuggable = true))
        assertFalse(access.isAllowed(null, debuggable = true))
        assertFalse(access.isAllowed(AgentAccess.ADB_SHELL_PACKAGE, debuggable = true))
        val open = access.copy(allowedPackages = setOf("com.example.agent"), allowAdbShell = true)
        assertTrue(open.isAllowed("com.example.agent", debuggable = false))
        assertTrue(open.isAllowed(AgentAccess.ADB_SHELL_PACKAGE, debuggable = true))
        // The adb switch never works in a release build.
        assertFalse(open.isAllowed(AgentAccess.ADB_SHELL_PACKAGE, debuggable = false))
        assertNotNull(open)
    }
}
