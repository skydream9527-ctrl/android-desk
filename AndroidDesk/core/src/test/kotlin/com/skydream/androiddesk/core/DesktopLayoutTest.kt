package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.layout.CellPosition
import com.skydream.androiddesk.core.layout.DeskItem
import com.skydream.androiddesk.core.layout.DeskItemKind
import com.skydream.androiddesk.core.layout.DesktopLayout
import com.skydream.androiddesk.core.layout.GridSpec
import com.skydream.androiddesk.core.layout.ShellChrome
import com.skydream.androiddesk.core.layout.shellChromeForWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLayoutTest {
    private fun empty() = DesktopLayout(grid = GridSpec(columns = 4, rows = 5, dockSlots = 5))

    private fun app(id: String, x: Int, y: Int, pkg: String = "pkg.$id") = DeskItem(
        id = id,
        kind = DeskItemKind.App,
        position = CellPosition(0, x, y),
        packageName = pkg,
    )

    @Test
    fun rejectsOverlappingItems() {
        val layout = empty().addItem(app("a", 0, 0))
        assertFailsWith<IllegalArgumentException> { layout.addItem(app("b", 0, 0)) }
    }

    @Test
    fun firstFreeCellSkipsOccupied() {
        val layout = empty().addItem(app("a", 0, 0))
        assertEquals(CellPosition(0, 1, 0), layout.firstFreeCell())
    }

    @Test
    fun removeUninstalledDropsStaleIconsAndDockPins() {
        var layout = empty()
            .addItem(app("keep", 0, 0, "com.keep"))
            .addItem(app("gone", 1, 0, "com.gone"))
        layout = layout.pinToDock("gone", 0)
        layout = layout.removeUninstalled(setOf("com.keep"))
        assertEquals(listOf("keep"), layout.items.map { it.id })
        assertNull(layout.dock[0].itemId)
    }

    @Test
    fun widthBreakpointSwitchesChrome() {
        assertEquals(ShellChrome.PhoneDock, shellChromeForWidth(411))
        assertEquals(ShellChrome.TabletTaskbar, shellChromeForWidth(600))
        assertEquals(ShellChrome.TabletTaskbar, shellChromeForWidth(800))
    }

    @Test
    fun cellsOnDifferentPagesDoNotOverlap() {
        val a = CellPosition(0, 0, 0, 2, 2)
        val b = CellPosition(1, 0, 0, 2, 2)
        assertTrue(!a.overlaps(b))
    }
}
