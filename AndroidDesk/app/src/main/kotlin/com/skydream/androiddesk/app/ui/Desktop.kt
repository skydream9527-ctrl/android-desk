package com.skydream.androiddesk.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydream.androiddesk.app.DeskController
import com.skydream.androiddesk.app.DeskUiState
import com.skydream.androiddesk.app.Screen
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.client.desktopChrome
import com.skydream.androiddesk.core.layout.CellPosition
import com.skydream.androiddesk.core.layout.DeskItem
import com.skydream.androiddesk.core.layout.ShellChrome
import com.skydream.androiddesk.core.layout.appKey
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PAGE_PADDING = 8.dp
private val DOCK_ICON = 50.dp
private val DRAG_ICON = 64.dp

/** An icon picked up by a long press. Positions are in root coordinates. */
private class DragState {
    var item by mutableStateOf<DeskItem?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var moved by mutableStateOf(false)
        private set
    private var origin = Offset.Zero

    val active: Boolean get() = item != null

    fun begin(item: DeskItem, at: Offset) {
        this.item = item
        pointer = at
        origin = at
        moved = false
    }

    fun moveTo(at: Offset, slop: Float) {
        pointer = at
        if (!moved && (at - origin).getDistance() > slop) moved = true
    }

    fun clear() {
        item = null
        moved = false
    }
}

/**
 * The desktop itself: wallpaper shows through, icon pages, page dots, dock.
 * Long-press an icon to pick it up; drop it on a cell, on the dock, or on the remove strip.
 * Releasing without moving opens that app's sheet instead.
 */
@Composable
fun DesktopCanvas(
    state: DeskUiState,
    controller: DeskController,
    onRequestDefaultHome: () -> Unit,
    onOpenAppSheet: (AppEntry) -> Unit,
    onOpenDeskMenu: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val chrome = remember(widthDp, state.homeRole) { desktopChrome(widthDp, state.homeRole) }
    val pageCount = state.layout.pageCount
    val pagerState = rememberPagerState(initialPage = state.currentPage.coerceIn(0, pageCount - 1)) { pageCount }
    val drag = remember { DragState() }
    var pagerRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var removeRect by remember { mutableStateOf(Rect.Zero) }
    val latestState by rememberUpdatedState(state)
    val latestOpenSheet by rememberUpdatedState(onOpenAppSheet)
    val pagePaddingPx = with(density) { PAGE_PADDING.toPx() }
    val removeReachPx = with(density) { 18.dp.toPx() }

    // The controller owns the current page (an assistant may turn pages too); the pager follows
    // it, and reports back where the user's own swipe ended.
    LaunchedEffect(state.currentPage, pageCount) {
        val target = state.currentPage.coerceIn(0, pageCount - 1)
        if (pagerState.targetPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { controller.onPageSettled(it) }
    }

    fun cellSize(): Offset {
        val grid = latestState.layout.grid
        return Offset((pagerRect.width - 2 * pagePaddingPx) / grid.columns, pagerRect.height / grid.rows)
    }

    fun cellAt(point: Offset): CellPosition {
        val grid = latestState.layout.grid
        val cell = cellSize()
        val x = ((point.x - pagerRect.left - pagePaddingPx) / cell.x).toInt().coerceIn(0, grid.columns - 1)
        val y = ((point.y - pagerRect.top) / cell.y).toInt().coerceIn(0, grid.rows - 1)
        return CellPosition(pagerState.currentPage, x, y)
    }

    fun drop() {
        val item = drag.item ?: return
        val entry = latestState.entryFor(item)
        val point = drag.pointer
        when {
            !drag.moved -> entry?.let { latestOpenSheet(it) }
            removeRect.inflate(removeReachPx).contains(point) -> controller.removeItem(item.id)
            dockRect.contains(point) -> entry?.let { controller.pinToDock(it) }
            else -> controller.moveItem(item.id, cellAt(point))
        }
        drag.clear()
        controller.trimPages()
    }

    // Holding a dragged icon at the left or right edge turns the page; past the last page it
    // opens a new one.
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        val edge = with(density) { 30.dp.toPx() }
        var dwell = 0L
        while (true) {
            delay(100)
            val x = drag.pointer.x
            val direction = when {
                !drag.moved -> 0
                x < pagerRect.left + edge -> -1
                x > pagerRect.right - edge -> 1
                else -> 0
            }
            if (direction == 0) {
                dwell = 0
                continue
            }
            dwell += 100
            if (dwell < 600) continue
            dwell = 0
            val layout = latestState.layout
            val target = pagerState.currentPage + direction
            when {
                target in 0 until layout.pageCount -> controller.setPage(target)
                target == layout.pageCount &&
                    layout.itemsOnPage(layout.pageCount - 1).any { it.id != drag.item?.id } ->
                    controller.setPage(controller.addPage())
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Swipe up anywhere on the desktop opens the drawer.
                val threshold = 56.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -threshold) controller.openScreen(Screen.Drawer) },
                )
            },
    ) {
        // Soft shading behind the status bar and the dock so text stays readable on any wallpaper.
        Box(
            Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0x4D000000), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x59000000)))),
        )

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (chrome.showPreviewBanner) {
                PreviewBanner(
                    text = chrome.previewBannerText.orEmpty(),
                    showAction = chrome.setDefaultHomeActionVisible,
                    onSetDefault = onRequestDefaultHome,
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { pagerRect = it.boundsInRoot() },
                userScrollEnabled = !drag.active,
                // Every page stays composed so an icon being dragged across pages keeps its gesture.
                beyondViewportPageCount = pageCount,
                key = { it },
            ) { page ->
                DeskPage(
                    page = page,
                    state = state,
                    drag = drag,
                    onLaunch = { controller.launch(it) },
                    onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDrop = ::drop,
                    onLongPressBlank = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenDeskMenu()
                    },
                )
            }
            PageDots(pageCount, pagerState.currentPage) { controller.setPage(it) }
            Dock(
                state = state,
                wide = chrome.shell == ShellChrome.TabletTaskbar,
                onLaunch = { controller.launch(it) },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenAppSheet(it)
                },
                onOpenDrawer = { controller.openScreen(Screen.Drawer) },
                modifier = Modifier.onGloballyPositioned { dockRect = it.boundsInRoot() },
            )
        }

        if (drag.active && drag.moved) {
            val overRemove = removeRect.inflate(removeReachPx).contains(drag.pointer)
            val pill = RoundedCornerShape(50)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .onGloballyPositioned { removeRect = it.boundsInRoot() }
                    .clip(pill)
                    .background(if (overRemove) DeskColors.danger else DeskColors.chromeStrong)
                    .border(1.dp, DeskColors.chromeBorder, pill)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    text = if (overRemove) "松手，从桌面拿走" else "拖到这里，从桌面拿走",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
            if (!overRemove && pagerRect.contains(drag.pointer)) {
                val cell = cellAt(drag.pointer)
                val size = cellSize()
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (pagerRect.left + pagePaddingPx + cell.cellX * size.x).roundToInt(),
                                (pagerRect.top + cell.cellY * size.y).roundToInt(),
                            )
                        }
                        .size(with(density) { size.x.toDp() }, with(density) { size.y.toDp() })
                        .padding(6.dp)
                        .clip(shape)
                        .background(Color(0x24FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), shape),
                )
            }
        }

        drag.item?.let { item ->
            val entry = state.entryFor(item)
            val key = entry?.key ?: appKey(item.packageName.orEmpty(), item.activityName)
            val iconPx = with(density) { DRAG_ICON.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (drag.pointer.x - iconPx / 2).roundToInt(),
                            (drag.pointer.y - iconPx * 0.8f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        alpha = 0.95f
                    },
            ) {
                StyledIcon(
                    art = rememberIconArt(entry),
                    label = entry?.label ?: item.label.orEmpty(),
                    seed = key,
                    style = state.iconTheme.styleFor(key),
                    size = DRAG_ICON,
                )
            }
        }
    }
}

@Composable
private fun DeskPage(
    page: Int,
    state: DeskUiState,
    drag: DragState,
    onLaunch: (AppEntry) -> Unit,
    onPickUp: () -> Unit,
    onDrop: () -> Unit,
    onLongPressBlank: () -> Unit,
) {
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestPickUp by rememberUpdatedState(onPickUp)
    val latestDrop by rememberUpdatedState(onDrop)
    val latestLongPressBlank by rememberUpdatedState(onLongPressBlank)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { latestLongPressBlank() }) }
            .padding(horizontal = PAGE_PADDING),
    ) {
        val grid = state.layout.grid
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        val iconSize = minOf(cellW * 0.62f, cellH * 0.56f, 64.dp).coerceAtLeast(36.dp)

        if (state.ready && page == 0 && state.layout.items.isEmpty()) {
            Text(
                text = "桌面还是空的\n长按空白处，从手机里添加应用",
                color = DeskColors.onWallpaperDim,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        state.layout.itemsOnPage(page).forEach { item ->
            key(item.id) {
                val entry = state.entryFor(item)
                val itemKey = entry?.key ?: appKey(item.packageName.orEmpty(), item.activityName)
                val label = entry?.label ?: item.label.orEmpty()
                val x by animateDpAsState(cellW * item.position.cellX, label = "cellX")
                val y by animateDpAsState(cellH * item.position.cellY, label = "cellY")
                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val beingDragged = drag.item?.id == item.id
                val highlighted = state.highlightKey != null && state.highlightKey == entry?.key
                val ring = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                        .size(cellW * item.position.spanX, cellH * item.position.spanY)
                        .onGloballyPositioned { coords = it }
                        .graphicsLayer { alpha = if (beingDragged) 0f else 1f }
                        .then(
                            if (highlighted) {
                                Modifier.padding(3.dp).background(Color(0x337FB0FF), ring).border(2.dp, DeskColors.accent, ring)
                            } else {
                                Modifier
                            },
                        )
                        // Tap first in the chain, pick-up after it: the drag detector then sees
                        // the release before the tap handler does and can claim it.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "打开",
                        ) { entry?.let { latestLaunch(it) } }
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local ->
                                    coords?.takeIf { it.isAttached }?.let {
                                        drag.begin(item, it.localToRoot(local))
                                        latestPickUp()
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    coords?.takeIf { it.isAttached }?.let {
                                        drag.moveTo(it.localToRoot(change.position), viewConfiguration.touchSlop)
                                    }
                                },
                                onDragEnd = { latestDrop() },
                                onDragCancel = { drag.clear() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    IconCell(entry, label, itemKey, state.iconTheme.styleFor(itemKey), iconSize, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PreviewBanner(text: String, showAction: Boolean, onSetDefault: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(DeskColors.chromeStrong)
            .border(1.dp, DeskColors.chromeBorder, shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(DeskColors.previewBadge))
        Spacer(Modifier.width(10.dp))
        Text(text, color = DeskColors.onWallpaper, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        if (showAction) {
            TextButton(onClick = onSetDefault) { Text("设为默认", color = DeskColors.previewBadge) }
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pageCount > 1) {
            repeat(pageCount) { index ->
                val selected = index == current
                Box(
                    Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "第 ${index + 1} 页",
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color(0x73FFFFFF)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    state: DeskUiState,
    wide: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier
                // A phone dock spans the screen; on a wide screen it is a centred taskbar.
                .then(if (wide) Modifier.widthIn(max = 640.dp) else Modifier)
                .fillMaxWidth()
                .clip(shape)
                .background(DeskColors.chrome)
                .border(1.dp, DeskColors.chromeBorder, shape)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.layout.dock.forEach { slot ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val entry = if (slot.isEmpty) null else state.entryFor(slot)
                    if (entry != null) {
                        StyledIcon(
                            art = rememberIconArt(entry),
                            label = entry.label,
                            seed = entry.key,
                            style = state.styleFor(entry),
                            size = DOCK_ICON,
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onLaunch(entry) },
                                    onLongClick = { onLongPress(entry) },
                                )
                                .semantics { contentDescription = entry.label },
                        )
                    } else {
                        Spacer(Modifier.size(DOCK_ICON))
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(DOCK_ICON)
                        .clip(CircleShape)
                        .background(Color(0x2EFFFFFF))
                        .clickable(onClickLabel = "打开应用抽屉", onClick = onOpenDrawer)
                        .semantics { contentDescription = "全部应用" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        val step = size.width / 2f
                        val radius = size.width * 0.085f
                        for (row in 0..2) {
                            for (column in 0..2) {
                                drawCircle(Color.White, radius, Offset(column * step, row * step))
                            }
                        }
                    }
                }
            }
        }
    }
}
