package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.layout.CellPosition
import com.skydream.androiddesk.core.layout.DesktopLayout
import com.skydream.androiddesk.core.layout.GridDensity
import com.skydream.androiddesk.core.layout.GridSpec
import com.skydream.androiddesk.core.layout.gridSpecFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopLayoutOpsTest {
    private fun empty(columns: Int = 2, rows: Int = 2, dock: Int = 2) =
        DesktopLayout.empty(GridSpec(columns, rows, dock))

    @Test
    fun placeAppFillsCellsInReadingOrderThenAddsAPage() {
        var layout = empty()
        listOf("a", "b", "c", "d", "e").forEach { layout = layout.placeApp("pkg.$it", "Main") }
        assertEquals(2, layout.pageCount)
        assertEquals(CellPosition(0, 0, 0), layout.appItem("pkg.a")!!.position)
        assertEquals(CellPosition(0, 1, 0), layout.appItem("pkg.b")!!.position)
        assertEquals(CellPosition(0, 1, 1), layout.appItem("pkg.d")!!.position)
        assertEquals(CellPosition(1, 0, 0), layout.appItem("pkg.e")!!.position)
    }

    @Test
    fun placingTheSameAppTwiceChangesNothing() {
        val once = empty().placeApp("pkg.a", "Main")
        assertSame(once, once.placeApp("pkg.a", "Main"))
    }

    @Test
    fun placeAppPrefersTheRequestedPageButFallsBackToEarlierOnes() {
        var layout = empty().addPage()
        layout = layout.placeApp("pkg.a", preferredPage = 1)
        assertEquals(1, layout.appItem("pkg.a")!!.position.pageIndex)
        // Fill page 1, then ask for page 1 again: the icon lands on page 0, no new page.
        listOf("b", "c", "d").forEach { layout = layout.placeApp("pkg.$it", preferredPage = 1) }
        layout = layout.placeApp("pkg.e", preferredPage = 1)
        assertEquals(0, layout.appItem("pkg.e")!!.position.pageIndex)
        assertEquals(2, layout.pageCount)
    }

    @Test
    fun removeAppLeavesItsDockPinAlone() {
        val layout = empty().placeApp("pkg.a").pinAppToDock("pkg.a").removeApp("pkg.a")
        assertFalse(layout.hasApp("pkg.a"))
        assertTrue(layout.dockHasApp("pkg.a"))
    }

    @Test
    fun moveToFreeCellAndSwapWithOccupant() {
        var layout = empty().placeApp("pkg.a").placeApp("pkg.b")
        val a = layout.appItem("pkg.a")!!
        val b = layout.appItem("pkg.b")!!
        layout = layout.moveItem(a.id, CellPosition(0, 1, 1))
        assertEquals(CellPosition(0, 1, 1), layout.item(a.id)!!.position)
        layout = layout.moveItem(a.id, b.position)
        assertEquals(b.position, layout.item(a.id)!!.position)
        assertEquals(CellPosition(0, 1, 1), layout.item(b.id)!!.position)
    }

    @Test
    fun moveOutsideTheGridOrToAMissingPageIsIgnored() {
        val layout = empty().placeApp("pkg.a")
        val id = layout.appItem("pkg.a")!!.id
        assertSame(layout, layout.moveItem(id, CellPosition(0, 5, 0)))
        assertSame(layout, layout.moveItem(id, CellPosition(3, 0, 0)))
        assertSame(layout, layout.moveItem("nope", CellPosition(0, 1, 1)))
    }

    @Test
    fun dockPinsUseFirstEmptySlotAndStopWhenFull() {
        var layout = empty(dock = 2).pinAppToDock("pkg.a").pinAppToDock("pkg.b")
        assertEquals(listOf("pkg.a", "pkg.b"), layout.dock.map { it.packageName })
        assertTrue(layout.dockIsFull)
        assertSame(layout, layout.pinAppToDock("pkg.c"))
        layout = layout.unpinAppFromDock("pkg.a").pinAppToDock("pkg.c")
        assertEquals(listOf("pkg.c", "pkg.b"), layout.dock.map { it.packageName })
    }

    @Test
    fun retainAppsDropsIconsAndDockPinsOfMissingApps() {
        val layout = empty()
            .placeApp("pkg.keep", "Main")
            .placeApp("pkg.gone", "Main")
            .pinAppToDock("pkg.gone", "Main")
            .pinAppToDock("pkg.keep", "Main")
            .retainApps { pkg, _ -> pkg == "pkg.keep" }
        assertEquals(listOf("pkg.keep"), layout.items.map { it.packageName })
        assertNull(layout.dock[0].itemId)
        assertEquals("pkg.keep", layout.dock[1].packageName)
    }

    @Test
    fun withGridKeepsFittingIconsAndRehomesTheRest() {
        var layout = DesktopLayout.empty(GridSpec(4, 2, 4))
        (0 until 8).forEach { layout = layout.placeApp("pkg.$it") }
        layout = layout.pinAppToDock("pkg.0").pinAppToDock("pkg.1").pinAppToDock("pkg.2")
        val narrow = layout.withGrid(GridSpec(2, 2, 2))
        assertEquals(8, narrow.items.size)
        assertTrue(narrow.items.all { it.position.fits(2, 2) })
        // No two icons share a cell.
        assertEquals(8, narrow.items.map { it.position }.toSet().size)
        assertEquals(2, narrow.pageCount)
        // Icons that already fit did not move.
        assertEquals(CellPosition(0, 1, 1), narrow.appItem("pkg.5")!!.position)
        // Dock shrinks from the end and keeps order.
        assertEquals(listOf("pkg.0", "pkg.1"), narrow.dock.map { it.packageName })
        assertEquals(listOf(0, 1), narrow.dock.map { it.index })
    }

    @Test
    fun trimTrailingEmptyPagesKeepsUsedPagesAndAtLeastOne() {
        val layout = empty().addPage().addPage().placeApp("pkg.a", preferredPage = 1)
        assertEquals(2, layout.trimTrailingEmptyPages().pageCount)
        assertEquals(1, empty().addPage().trimTrailingEmptyPages().pageCount)
    }

    @Test
    fun gridSizingUsesBothSidesAndIsStableAcrossRotation() {
        val phone = gridSpecFor(411, 891)
        assertEquals(GridSpec(4, 6, 4), phone)
        assertEquals(5, gridSpecFor(411, 891, GridDensity.Compact).columns)
        assertTrue(gridSpecFor(411, 891, GridDensity.Loose).rows < phone.rows)
        // A small phone still gets a usable grid.
        assertEquals(4, gridSpecFor(320, 568).rows)
        val tablet = gridSpecFor(800, 1280)
        assertEquals(GridSpec(6, 5, 6), tablet)
        assertNotNull(tablet)
    }
}
