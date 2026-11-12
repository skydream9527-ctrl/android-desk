package com.skydream.androiddesk.client

import com.skydream.androiddesk.core.home.HomeRoleState
import com.skydream.androiddesk.core.layout.ShellChrome
import com.skydream.androiddesk.core.layout.shellChromeForWidth

data class DesktopChrome(
    val shell: ShellChrome,
    val showPreviewBanner: Boolean,
    val previewBannerText: String?,
    val setDefaultHomeActionVisible: Boolean,
)

fun desktopChrome(widthDp: Int, role: HomeRoleState): DesktopChrome {
    return DesktopChrome(
        shell = shellChromeForWidth(widthDp),
        showPreviewBanner = role.showsPreviewBanner,
        previewBannerText = role.bannerText,
        setDefaultHomeActionVisible = !role.isDefaultHome,
    )
}
