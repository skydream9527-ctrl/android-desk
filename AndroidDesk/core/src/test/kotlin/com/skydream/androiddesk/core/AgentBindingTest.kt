package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.agent.AgentBinding
import com.skydream.androiddesk.core.agent.BundledAgentCatalog
import com.skydream.androiddesk.core.agent.SurfaceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentBindingTest {
    @Test
    fun residentSurfaceRequiresAllowAndInstall() {
        val binding = AgentBinding(
            agentId = "sample",
            packageName = "com.example.agent",
            surfaces = setOf(SurfaceKind.PET),
            allowReside = true,
        )
        assertFalse(binding.canShowResidentSurface(emptySet()))
        assertTrue(binding.canShowResidentSurface(setOf("com.example.agent")))
    }

    @Test
    fun dshGoBindingUsesVerifiedPackageName() {
        assertTrue(BundledAgentCatalog.DshGo.packageNameVerified)
        val binding = BundledAgentCatalog.DshGo.toBinding(setOf(SurfaceKind.PET, SurfaceKind.DOCK_ITEM))
        assertEquals("io.github.ccch1mneyyy.dsh_app", binding.packageName)
        assertFalse(binding.cooperative)
    }

    @Test
    fun unverifiedDeepDeepRefusesToBind() {
        assertFalse(BundledAgentCatalog.DeepDeep.packageNameVerified)
        assertFailsWith<IllegalArgumentException> {
            BundledAgentCatalog.DeepDeep.toBinding(setOf(SurfaceKind.PET))
        }
    }
}
