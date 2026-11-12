package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.home.HomeRoleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeRoleTest {
    @Test
    fun previewNeverClaimsToBeDefaultHome() {
        val state = HomeRoleState.preview()
        assertFalse(state.isDefaultHome)
        assertTrue(state.showsPreviewBanner)
        assertEquals("预览模式 · 尚未设为默认主屏幕", state.bannerText)
    }

    @Test
    fun defaultHomeHasNoPreviewBanner() {
        val state = HomeRoleState.defaultHome()
        assertTrue(state.isDefaultHome)
        assertFalse(state.showsPreviewBanner)
        assertNull(state.bannerText)
    }
}
