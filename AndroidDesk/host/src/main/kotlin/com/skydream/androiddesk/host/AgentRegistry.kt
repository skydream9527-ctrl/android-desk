package com.skydream.androiddesk.host

import com.skydream.androiddesk.core.agent.AgentBinding
import com.skydream.androiddesk.core.agent.KnownAgent
import com.skydream.androiddesk.core.agent.SurfaceKind

/**
 * Resolves installed packages to bindings. Does not invent package names
 * for catalog entries that have not been verified.
 */
class AgentRegistry(
    private val catalog: List<KnownAgent>,
) {
    fun installedBindings(
        installedPackages: Set<String>,
        allowReside: Set<String> = emptySet(),
    ): List<AgentBinding> {
        return catalog.mapNotNull { agent ->
            val pkg = agent.packageName
            if (!agent.packageNameVerified || pkg.isNullOrBlank()) return@mapNotNull null
            if (pkg !in installedPackages) return@mapNotNull null
            agent.toBinding(
                surfaces = setOf(SurfaceKind.DOCK_ITEM, SurfaceKind.PET),
                allowReside = agent.agentId in allowReside,
                cooperative = false,
            )
        }
    }
}
