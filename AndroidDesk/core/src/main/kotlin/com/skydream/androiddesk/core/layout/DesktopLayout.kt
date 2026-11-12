package com.skydream.androiddesk.core.layout

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
enum class DeskItemKind {
    App,
    Folder,
    Widget,
}

@Serializable
data class DeskItem(
    val id: String,
    val kind: DeskItemKind,
    val position: CellPosition,
    val packageName: String? = null,
    val label: String? = null,
    val childIds: List<String> = emptyList(),
    /** Launcher activity class. Null means "whatever the package's main launcher activity is". */
    val activityName: String? = null,
)

/**
 * A dock slot. [itemId] is null when the slot is empty. A slot pinned straight from the
 * installed-app list carries its own [packageName] / [activityName] and does not need a
 * matching icon on a desktop page.
 */
@Serializable
data class DockSlot(
    val index: Int,
    val itemId: String?,
    val packageName: String? = null,
    val activityName: String? = null,
) {
    val isEmpty: Boolean get() = itemId == null
}

@Serializable
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

/** Stable key for one launchable entry of an installed app. */
fun appKey(packageName: String, activityName: String?): String =
    if (activityName.isNullOrBlank()) packageName else "$packageName/$activityName"

@Serializable
data class DesktopLayout(
    val grid: GridSpec,
    val items: List<DeskItem> = emptyList(),
    val dock: List<DockSlot> = (0 until 5).map { DockSlot(it, null) },
    val pageCount: Int = 1,
) {
    fun item(id: String): DeskItem? = items.find { it.id == id }

    fun occupying(position: CellPosition): DeskItem? =
        items.find { it.position.overlaps(position) }

    fun itemsOnPage(pageIndex: Int): List<DeskItem> = items.filter { it.position.pageIndex == pageIndex }

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
            if (slot.itemId == id) DockSlot(slot.index, null) else slot
        }
        return copy(items = nextItems, dock = nextDock)
    }

    fun removeUninstalled(installedPackages: Set<String>): DesktopLayout =
        retainApps { packageName, _ -> packageName in installedPackages }

    /**
     * Drops app icons and dock pins for which [isPresent] is false. Used both for
     * uninstalled packages and for a launcher activity that disappeared in an update.
     */
    fun retainApps(isPresent: (packageName: String, activityName: String?) -> Boolean): DesktopLayout {
        val staleIds = items
            .filter { it.kind == DeskItemKind.App && it.packageName != null && !isPresent(it.packageName, it.activityName) }
            .map { it.id }
            .toSet()
        val withoutItems = staleIds.fold(this) { acc, id -> acc.removeItem(id) }
        val nextDock = withoutItems.dock.map { slot ->
            val pkg = slot.packageName
            if (pkg != null && !isPresent(pkg, slot.activityName)) DockSlot(slot.index, null) else slot
        }
        return if (nextDock == withoutItems.dock) withoutItems else withoutItems.copy(dock = nextDock)
    }

    fun pinToDock(itemId: String, slotIndex: Int): DesktopLayout {
        require(slotIndex in dock.indices) { "dock slot out of range" }
        require(items.any { it.id == itemId } || dock.any { it.itemId == itemId }) {
            "item is not on the desktop or dock"
        }
        val source = item(itemId)
        val existing = dock.find { it.itemId == itemId }
        val next = dock.toMutableList()
        // If this item was pinned elsewhere, clear it first.
        for (i in next.indices) {
            if (next[i].itemId == itemId) next[i] = DockSlot(i, null)
        }
        next[slotIndex] = DockSlot(
            index = slotIndex,
            itemId = itemId,
            packageName = source?.packageName ?: existing?.packageName,
            activityName = source?.activityName ?: existing?.activityName,
        )
        return copy(dock = next)
    }

    fun firstFreeCell(pageIndex: Int = 0, spanX: Int = 1, spanY: Int = 1): CellPosition? {
        for (y in 0..grid.rows - spanY) {
            for (x in 0..grid.columns - spanX) {
                val pos = CellPosition(pageIndex, x, y, spanX, spanY)
                if (items.none { it.position.overlaps(pos) }) return pos
            }
        }
        return null
    }

    /** First free cell on [startPage] or any later page. */
    fun firstFreeCellFrom(startPage: Int = 0, spanX: Int = 1, spanY: Int = 1): CellPosition? {
        for (page in startPage.coerceAtLeast(0) until pageCount) {
            firstFreeCell(page, spanX, spanY)?.let { return it }
        }
        return null
    }

    fun addPage(): DesktopLayout = copy(pageCount = pageCount + 1)

    /** Removes empty pages at the end, always keeping at least one page. */
    fun trimTrailingEmptyPages(): DesktopLayout {
        val lastUsed = items.maxOfOrNull { it.position.pageIndex } ?: 0
        val next = (lastUsed + 1).coerceAtLeast(1)
        return if (next < pageCount) copy(pageCount = next) else this
    }

    // ---- Apps on desktop pages ----

    fun appItem(packageName: String, activityName: String? = null): DeskItem? = items.find {
        it.kind == DeskItemKind.App && it.packageName == packageName &&
            (activityName == null || it.activityName == null || it.activityName == activityName)
    }

    fun hasApp(packageName: String, activityName: String? = null): Boolean =
        appItem(packageName, activityName) != null

    /**
     * Puts an app icon in the first free cell at or after [preferredPage]; falls back to
     * earlier pages, then appends a page. Adding an app that is already there changes nothing.
     */
    fun placeApp(
        packageName: String,
        activityName: String? = null,
        label: String? = null,
        preferredPage: Int = 0,
    ): DesktopLayout {
        if (hasApp(packageName, activityName)) return this
        var target = this
        val start = preferredPage.coerceIn(0, pageCount - 1)
        val cell = firstFreeCellFrom(start) ?: firstFreeCellFrom(0) ?: run {
            target = addPage()
            CellPosition(target.pageCount - 1, 0, 0)
        }
        return target.addItem(
            DeskItem(
                id = DESK_ID_PREFIX + appKey(packageName, activityName),
                kind = DeskItemKind.App,
                position = cell,
                packageName = packageName,
                label = label,
                activityName = activityName,
            ),
        )
    }

    /** Takes the app's icon(s) off the desktop pages. Dock pins are left alone. */
    fun removeApp(packageName: String, activityName: String? = null): DesktopLayout {
        val ids = items
            .filter {
                it.kind == DeskItemKind.App && it.packageName == packageName &&
                    (activityName == null || it.activityName == null || it.activityName == activityName)
            }
            .map { it.id }
        return ids.fold(this) { acc, id -> acc.removeItem(id) }
    }

    /**
     * Moves an item to [to]. If one item of the same size sits there, the two swap places.
     * Returns the layout unchanged when the move is not possible.
     */
    fun moveItem(id: String, to: CellPosition): DesktopLayout {
        val moving = item(id) ?: return this
        val target = to.copy(spanX = moving.position.spanX, spanY = moving.position.spanY)
        if (target.pageIndex >= pageCount || !target.fits(grid.columns, grid.rows)) return this
        if (target == moving.position) return this
        val blockers = items.filter { it.id != id && it.position.overlaps(target) }
        return when {
            blockers.isEmpty() ->
                copy(items = items.map { if (it.id == id) it.copy(position = target) else it })
            blockers.size == 1 && blockers[0].position.copy(pageIndex = 0, cellX = 0, cellY = 0) ==
                moving.position.copy(pageIndex = 0, cellX = 0, cellY = 0) &&
                blockers[0].position == target -> {
                val other = blockers[0]
                copy(
                    items = items.map {
                        when (it.id) {
                            id -> it.copy(position = target)
                            other.id -> it.copy(position = moving.position)
                            else -> it
                        }
                    },
                )
            }
            else -> this
        }
    }

    // ---- Dock ----

    val dockIsFull: Boolean get() = dock.none { it.isEmpty }

    fun dockSlotOf(packageName: String, activityName: String? = null): DockSlot? = dock.find {
        it.packageName == packageName &&
            (activityName == null || it.activityName == null || it.activityName == activityName)
    }

    fun dockHasApp(packageName: String, activityName: String? = null): Boolean =
        dockSlotOf(packageName, activityName) != null

    /**
     * Pins an installed app into [slotIndex], or the first empty slot. Unchanged if the app
     * is already pinned or the dock has no room.
     */
    fun pinAppToDock(packageName: String, activityName: String? = null, slotIndex: Int? = null): DesktopLayout {
        if (dockHasApp(packageName, activityName)) return this
        val index = slotIndex ?: dock.firstOrNull { it.isEmpty }?.index ?: return this
        if (index !in dock.indices) return this
        val next = dock.toMutableList()
        next[index] = DockSlot(index, DOCK_ID_PREFIX + appKey(packageName, activityName), packageName, activityName)
        return copy(dock = next)
    }

    fun unpinAppFromDock(packageName: String, activityName: String? = null): DesktopLayout {
        val slot = dockSlotOf(packageName, activityName) ?: return this
        return copy(dock = dock.map { if (it.index == slot.index) DockSlot(it.index, null) else it })
    }

    // ---- Grid changes ----

    /**
     * Re-fits the layout to another grid. Icons whose cell still exists stay put; the rest
     * are re-placed in reading order starting from their own page. Positions are cells,
     * never pixels, so this is all a density or screen-size change needs.
     */
    fun withGrid(newGrid: GridSpec): DesktopLayout {
        if (newGrid == grid) return this
        val pinned = dock.filterNot { it.isEmpty }.take(newGrid.dockSlots)
        val newDock = (0 until newGrid.dockSlots).map { i ->
            pinned.getOrNull(i)?.copy(index = i) ?: DockSlot(i, null)
        }
        var next = DesktopLayout(grid = newGrid, items = emptyList(), dock = newDock, pageCount = pageCount)
        val ordered = items.sortedWith(
            compareBy({ it.position.pageIndex }, { it.position.cellY }, { it.position.cellX }),
        )
        val (stay, move) = ordered.partition { it.position.fits(newGrid.columns, newGrid.rows) }
        stay.forEach { next = next.addItem(it) }
        move.forEach { item ->
            val spanX = item.position.spanX.coerceAtMost(newGrid.columns)
            val spanY = item.position.spanY.coerceAtMost(newGrid.rows)
            val cell = next.firstFreeCellFrom(item.position.pageIndex, spanX, spanY) ?: run {
                next = next.addPage()
                CellPosition(next.pageCount - 1, 0, 0, spanX, spanY)
            }
            next = next.addItem(item.copy(position = cell))
        }
        return next
    }

    companion object {
        const val DESK_ID_PREFIX = "app:"
        const val DOCK_ID_PREFIX = "dock:"

        fun empty(grid: GridSpec): DesktopLayout = DesktopLayout(
            grid = grid,
            dock = (0 until grid.dockSlots).map { DockSlot(it, null) },
        )
    }
}

enum class ShellChrome {
    PhoneDock,
    TabletTaskbar,
}

fun shellChromeForWidth(widthDp: Int): ShellChrome =
    if (widthDp >= 600) ShellChrome.TabletTaskbar else ShellChrome.PhoneDock
