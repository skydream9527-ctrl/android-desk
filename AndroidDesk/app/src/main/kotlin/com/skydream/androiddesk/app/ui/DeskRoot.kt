package com.skydream.androiddesk.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydream.androiddesk.app.DeskController
import com.skydream.androiddesk.app.DeskUiState
import com.skydream.androiddesk.app.Screen
import com.skydream.androiddesk.app.data.AppEntry

/** Connects the existing desktop to the style screens while the rest of the launcher shell is built. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeskRoot(
    controller: DeskController,
    onRequestDefaultHome: () -> Unit,
    onLightSystemBars: (Boolean) -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var appKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeskMenu by rememberSaveable { mutableStateOf(false) }
    val openAppSheet: (AppEntry) -> Unit = { appKey = it.key }
    val lightBars = state.screen == Screen.IconStyle && !isSystemInDarkTheme()
    SideEffect { onLightSystemBars(lightBars) }

    BackHandler(state.screen != Screen.Desktop || state.homeRole.isDefaultHome) { controller.back() }

    CompositionLocalProvider(LocalIconLoader provides controller.icons) {
        DeskTheme {
            Box(Modifier.fillMaxSize()) {
                DesktopCanvas(state, controller, onRequestDefaultHome, openAppSheet) { showDeskMenu = true }
                when (state.screen) {
                    Screen.IconStyle -> IconStyleScreen(state, controller, onBack = { controller.back() })
                    Screen.Drawer -> LauncherDrawer(state, onLaunch = { controller.launch(it) }, onOpenAppSheet = openAppSheet)
                    else -> Unit
                }
            }
            if (showDeskMenu) {
                ModalBottomSheet(onDismissRequest = { showDeskMenu = false }) {
                    TextButton(
                        onClick = {
                            showDeskMenu = false
                            controller.openScreen(Screen.IconStyle)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) { Text("图标样式") }
                }
            }
            appKey?.let(state.appsByKey::get)?.let { entry ->
                ModalBottomSheet(onDismissRequest = { appKey = null }) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StyledIcon(rememberIconArt(entry), entry.label, entry.key, state.styleFor(entry), 52.dp)
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    entry.packageName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = {
                                appKey = null
                                controller.launch(entry)
                            }) { Text("打开") }
                        }
                        AppIconStyleSection(entry, state, controller)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherDrawer(
    state: DeskUiState,
    onLaunch: (AppEntry) -> Unit,
    onOpenAppSheet: (AppEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val apps = state.drawerApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    Surface(Modifier.fillMaxSize(), color = DeskColors.drawerScrim, contentColor = DeskColors.onWallpaper) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text("搜索应用") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (state.ready && query.isNotBlank() && apps.isEmpty()) {
                Text("没有找到「$query」", Modifier.padding(top = 16.dp))
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(84.dp), Modifier.padding(top = 12.dp)) {
                items(apps, key = { it.key }) { entry ->
                    Box(
                        Modifier.height(104.dp)
                            .combinedClickable(onClick = { onLaunch(entry) }, onLongClick = { onOpenAppSheet(entry) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconCell(entry, entry.label, entry.key, state.styleFor(entry), 56.dp, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
