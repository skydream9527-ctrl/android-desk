package com.skydream.androiddesk.core.layout

data class CellPosition(
    val pageIndex: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be >= 0" }
        require(cellX >= 0 && cellY >= 0) { "cell origin must be >= 0" }
        require(spanX >= 1 && spanY >= 1) { "span must be >= 1" }
    }

    fun overlaps(other: CellPosition): Boolean {
        if (pageIndex != other.pageIndex) return false
        val ax2 = cellX + spanX
        val ay2 = cellY + spanY
        val bx2 = other.cellX + other.spanX
        val by2 = other.cellY + other.spanY
        return cellX < bx2 && ax2 > other.cellX && cellY < by2 && ay2 > other.cellY
    }

    fun fits(columns: Int, rows: Int): Boolean {
        return cellX + spanX <= columns && cellY + spanY <= rows
    }
}

enum class DeskItemKind {
    App,
    Folder,
    Widget,
}

data class DeskItem(
    val id: String,
    val kind: DeskItemKind,
    val position: CellPosition,
    val packageName: String? = null,
    val label: String? = null,
    val childIds: List<String> = emptyList(),
)

data class DockSlot(
    val index: Int,
    val itemId: String?,
)

data class GridSpec(
    val columns: Int,
    val rows: Int,
    val dockSlots: Int = 5,
) {
    init {
        require(columns >= 1 && rows >= 1)
        require(dockSlots >= 1)
    }
}

data class DesktopLayout(
    val grid: GridSpec,
    val items: List<DeskItem> = emptyList(),
    val dock: List<DockSlot> = (0 until 5).map { DockSlot(it, null) },
    val pageCount: Int = 1,
) {
    fun item(id: String): DeskItem? = items.find { it.id == id }

    fun occupying(position: CellPosition): DeskItem? =
        items.find { it.position.overlaps(position) }

    fun addItem(item: DeskItem): DesktopLayout {
        require(item.position.pageIndex < pageCount) { "page does not exist" }
        require(item.position.fits(grid.columns, grid.rows)) { "item does not fit the grid" }
        require(occupying(item.position) == null) { "cell is occupied" }
        require(items.none { it.id == item.id }) { "duplicate item id" }
        return copy(items = items + item)
    }

    fun removeItem(id: String): DesktopLayout {
        val nextItems = items.filterNot { it.id == id }
        val nextDock = dock.map { slot ->
            if (slot.itemId == id) slot.copy(itemId = null) else slot
        }
        return copy(items = nextItems, dock = nextDock)
    }

    fun removeUninstalled(installedPackages: Set<String>): DesktopLayout {
        val staleIds = items
            .filter { it.kind == DeskItemKind.App && it.packageName != null && it.packageName !in installedPackages }
            .map { it.id }
            .toSet()
        return staleIds.fold(this) { acc, id -> acc.removeItem(id) }
    }

    fun pinToDock(itemId: String, slotIndex: Int): DesktopLayout {
        require(slotIndex in dock.indices) { "dock slot out of range" }
        require(items.any { it.id == itemId } || dock.any { it.itemId == itemId }) {
            "item is not on the desktop or dock"
        }
        val next = dock.toMutableList()
        // If this item was pinned elsewhere, clear it first.
        for (i in next.indices) {
            if (next[i].itemId == itemId) next[i] = next[i].copy(itemId = null)
        }
        next[slotIndex] = DockSlot(slotIndex, itemId)
        return copy(dock = next)
    }

    fun firstFreeCell(pageIndex: Int = 0): CellPosition? {
        for (y in 0 until grid.rows) {
            for (x in 0 until grid.columns) {
                val pos = CellPosition(pageIndex, x, y)
                if (occupying(pos) == null) return pos
            }
        }
        return null
    }
}

enum class ShellChrome {
    PhoneDock,
    TabletTaskbar,
}

fun shellChromeForWidth(widthDp: Int): ShellChrome =
    if (widthDp >= 600) ShellChrome.TabletTaskbar else ShellChrome.PhoneDock
