package com.skydream.androiddesk.client

import com.skydream.androiddesk.core.home.HomeRoleState
import com.skydream.androiddesk.core.layout.ShellChrome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopChromeTest {
    @Test
    fun previewOnPhoneShowsBannerAndSetDefaultAction() {
        val chrome = desktopChrome(411, HomeRoleState.preview())
        assertEquals(ShellChrome.PhoneDock, chrome.shell)
        assertTrue(chrome.showPreviewBanner)
        assertTrue(chrome.setDefaultHomeActionVisible)
    }

    @Test
    fun defaultHomeOnTabletHidesPreviewChrome() {
        val chrome = desktopChrome(800, HomeRoleState.defaultHome())
        assertEquals(ShellChrome.TabletTaskbar, chrome.shell)
        assertFalse(chrome.showPreviewBanner)
        assertFalse(chrome.setDefaultHomeActionVisible)
    }
}
