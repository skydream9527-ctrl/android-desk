package com.skydream.androiddesk.core.state

import com.skydream.androiddesk.core.control.AgentAccess
import com.skydream.androiddesk.core.icon.IconTheme
import com.skydream.androiddesk.core.layout.DesktopLayout
import com.skydream.androiddesk.core.layout.GridDensity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeskSettings(
    val density: GridDensity = GridDensity.Auto,
    /** App keys the user hid from the drawer. They stay installed and stay listed in app management. */
    val hiddenApps: Set<String> = emptySet(),
    val agentAccess: AgentAccess = AgentAccess(),
    /** "package/activity" of the launcher the user goes back to with "回到系统桌面". */
    val systemLauncher: String? = null,
    /** True once the first-run starter icons were placed, so an emptied desktop stays empty. */
    val seeded: Boolean = false,
    /** Where the assistant pet stands, as a fraction of the free desktop area (0..1). */
    val petX: Float = 0.92f,
    val petY: Float = 0.62f,
)

/** Everything this desktop persists. One file, on device only. */
@Serializable
data class DeskSnapshot(
    val layout: DesktopLayout,
    val iconTheme: IconTheme = IconTheme(),
    val settings: DeskSettings = DeskSettings(),
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

object DeskSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: DeskSnapshot): String = json.encodeToString(DeskSnapshot.serializer(), snapshot)

    /** Returns null for missing, corrupt or structurally invalid data; the caller starts fresh. */
    fun decode(text: String?): DeskSnapshot? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(DeskSnapshot.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }
}
