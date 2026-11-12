package com.skydream.androiddesk.core.layout

import kotlinx.serialization.Serializable

/** Grid density is a cell-size choice, not a phone/tablet switch. */
@Serializable
enum class GridDensity(val label: String) {
    Auto("自动"),
    Compact("紧凑"),
    Loose("宽松"),
}

/**
 * Picks the grid from the device's short and long side (in dp), so the same grid is used in
 * both orientations and rotating never reshuffles icons. Only a density change or a
 * different screen (fold / unfold) produces another grid.
 */
fun gridSpecFor(shortSideDp: Int, longSideDp: Int, density: GridDensity = GridDensity.Auto): GridSpec {
    val tablet = shellChromeForWidth(shortSideDp) == ShellChrome.TabletTaskbar
    return if (tablet) {
        val columns = when (density) {
            GridDensity.Auto -> 6
            GridDensity.Compact -> 8
            GridDensity.Loose -> 5
        }
        val rowHeight = when (density) {
            GridDensity.Auto -> 120
            GridDensity.Compact -> 104
            GridDensity.Loose -> 140
        }
        // Landscape is the tight orientation on a tablet, so rows come from the short side.
        val rows = ((shortSideDp - 200) / rowHeight).coerceIn(3, 6)
        GridSpec(columns = columns, rows = rows, dockSlots = 6)
    } else {
        val columns = if (density == GridDensity.Compact) 5 else 4
        val rowHeight = when (density) {
            GridDensity.Auto -> 104
            GridDensity.Compact -> 92
            GridDensity.Loose -> 124
        }
        val rows = ((longSideDp - 220) / rowHeight).coerceIn(4, 7)
        GridSpec(columns = columns, rows = rows, dockSlots = 4)
    }
}
