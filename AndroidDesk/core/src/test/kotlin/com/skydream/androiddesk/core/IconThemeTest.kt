package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.icon.IconShape
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconStyleOverride
import com.skydream.androiddesk.core.icon.IconTheme
import com.skydream.androiddesk.core.icon.IconTreatment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IconThemeTest {
    @Test
    fun overrideOnlyReplacesTheFieldsItSets() {
        val theme = IconTheme(IconStyle(shape = IconShape.Circle, treatment = IconTreatment.Glass, sizePercent = 110))
            .withOverride("pkg.a", IconStyleOverride(shape = IconShape.Hexagon))
        val a = theme.styleFor("pkg.a")
        assertEquals(IconShape.Hexagon, a.shape)
        assertEquals(IconTreatment.Glass, a.treatment)
        assertEquals(110, a.sizePercent)
        assertEquals(IconShape.Circle, theme.styleFor("pkg.b").shape)
    }

    @Test
    fun changingTheGlobalStyleStillRespectsOverrides() {
        val theme = IconTheme()
            .withOverride("pkg.a", IconStyleOverride(treatment = IconTreatment.Letter))
            .withGlobal(IconStyle(shape = IconShape.Flower, treatment = IconTreatment.Tinted))
        assertEquals(IconTreatment.Letter, theme.styleFor("pkg.a").treatment)
        assertEquals(IconShape.Flower, theme.styleFor("pkg.a").shape)
    }

    @Test
    fun emptyOverrideIsRemovedNotStored() {
        val theme = IconTheme()
            .withOverride("pkg.a", IconStyleOverride(shape = IconShape.Circle))
            .withOverride("pkg.a", IconStyleOverride())
        assertTrue(theme.overrides.isEmpty())
    }

    @Test
    fun pruneForgetsUninstalledApps() {
        val theme = IconTheme()
            .withOverride("pkg.a", IconStyleOverride(shape = IconShape.Circle))
            .withOverride("pkg.b", IconStyleOverride(shape = IconShape.Circle))
            .pruned(setOf("pkg.b"))
        assertEquals(setOf("pkg.b"), theme.overrides.keys)
    }

    @Test
    fun sizeIsBounded() {
        assertFailsWith<IllegalArgumentException> { IconStyle(sizePercent = 20) }
        assertFailsWith<IllegalArgumentException> { IconStyle(sizePercent = 400) }
    }
}
