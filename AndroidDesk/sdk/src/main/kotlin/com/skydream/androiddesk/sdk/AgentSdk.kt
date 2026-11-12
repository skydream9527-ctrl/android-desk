package com.skydream.androiddesk.sdk

/**
 * Draft of the stable surface for cooperative assistants (M4).
 * This module currently holds types only; the binder / AIDL implementation is not written yet.
 */
enum class AgentCapability {
    Pet,
    Panel,
    DesktopActions,
    Screenshot,
}

data class AgentHello(
    val sdkVersion: Int,
    val agentId: String,
    val displayName: String,
    val capabilities: Set<AgentCapability>,
)

data class SessionToken(val value: String)
