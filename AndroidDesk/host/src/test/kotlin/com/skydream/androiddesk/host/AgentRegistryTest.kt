package com.skydream.androiddesk.host

import com.skydream.androiddesk.core.agent.BundledAgentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRegistryTest {
    private val registry = AgentRegistry(
        listOf(BundledAgentCatalog.DshGo, BundledAgentCatalog.DeepDeep),
    )

    @Test
    fun skipsUnverifiedAndUninstalledAgents() {
        assertTrue(registry.installedBindings(emptySet()).isEmpty())
        val found = registry.installedBindings(
            installedPackages = setOf("io.github.ccch1mneyyy.dsh_app"),
            allowReside = setOf("dsh-go"),
        )
        assertEquals(1, found.size)
        assertEquals("dsh-go", found.single().agentId)
        assertTrue(found.single().allowReside)
    }
}
