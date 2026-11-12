package com.skydream.androiddesk.core.home

/**
 * Whether this installation is actually the device home screen.
 * Preview must never be reported as the default home.
 */
enum class HomeRole {
    Preview,
    DefaultHome,
}

data class HomeRoleState(
    val role: HomeRole,
) {
    val isDefaultHome: Boolean get() = role == HomeRole.DefaultHome
    val showsPreviewBanner: Boolean get() = role == HomeRole.Preview

    val bannerText: String?
        get() = when (role) {
            HomeRole.Preview -> "预览模式 · 尚未设为默认主屏幕"
            HomeRole.DefaultHome -> null
        }

    companion object {
        fun preview() = HomeRoleState(HomeRole.Preview)
        fun defaultHome() = HomeRoleState(HomeRole.DefaultHome)
    }
}
