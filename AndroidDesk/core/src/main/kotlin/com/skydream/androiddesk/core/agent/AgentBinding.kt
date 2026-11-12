package com.skydream.androiddesk.core.agent

enum class SurfaceKind {
    PET,
    PANEL,
    DESK_WINDOW,
    DOCK_ITEM,
}

enum class MatchStrategy {
    InstalledPackage,
}

enum class AgentPresenceState {
    Idle,
    Thinking,
    Acting,
    NeedsUser,
    Stopped,
}

data class AgentBinding(
    val agentId: String,
    val packageName: String,
    val surfaces: Set<SurfaceKind>,
    val matchStrategy: MatchStrategy = MatchStrategy.InstalledPackage,
    val cooperative: Boolean = false,
    val allowReside: Boolean = false,
) {
    init {
        require(agentId.isNotBlank())
        require(packageName.isNotBlank()) { "packageName must be a verified installed application id" }
        require(surfaces.isNotEmpty()) { "an agent binding must declare at least one surface" }
    }

    /**
     * A pet or panel may only be placed on the desktop when the user allowed residency
     * and the package is still installed. Non-cooperative apps can still be launched
     * as ordinary icons regardless of this.
     */
    fun canShowResidentSurface(installedPackages: Set<String>): Boolean =
        allowReside && packageName in installedPackages &&
            surfaces.any { it == SurfaceKind.PET || it == SurfaceKind.PANEL }
}

data class KnownAgent(
    val agentId: String,
    val displayName: String,
    val packageName: String? = null,
    val packageNameVerified: Boolean = false,
) {
    fun toBinding(
        surfaces: Set<SurfaceKind>,
        allowReside: Boolean = false,
        cooperative: Boolean = false,
    ): AgentBinding {
        val pkg = packageName
        require(packageNameVerified && !pkg.isNullOrBlank()) {
            "refusing to bind $agentId: package name is not verified"
        }
        return AgentBinding(
            agentId = agentId,
            packageName = pkg,
            surfaces = surfaces,
            allowReside = allowReside,
            cooperative = cooperative,
        )
    }
}

object BundledAgentCatalog {
    /** First integration target. Package name is filled in only after it is read from a real APK or listing. */
    val DshGo = KnownAgent(
        agentId = "dsh-go",
        displayName = "DSH Go",
        // Read from the public dsh-Go app/android/app/build.gradle.kts (applicationId).
        packageName = "io.github.ccch1mneyyy.dsh_app",
        packageNameVerified = true,
    )

    /** Same-class slot. The product and package name are still an open question (PRD Q1). */
    val DeepDeep = KnownAgent(
        agentId = "deepdeep",
        displayName = "DeepDeep",
        packageName = null,
        packageNameVerified = false,
    )

    fun byId(agentId: String): KnownAgent? = when (agentId) {
        DshGo.agentId -> DshGo
        DeepDeep.agentId -> DeepDeep
        else -> null
    }
}
