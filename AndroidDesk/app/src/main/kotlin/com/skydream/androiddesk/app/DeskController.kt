package com.skydream.androiddesk.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.skydream.androiddesk.app.agent.AgentHub
import com.skydream.androiddesk.app.agent.AgentReply
import com.skydream.androiddesk.app.data.AppCatalog
import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.data.DeskStore
import com.skydream.androiddesk.app.data.IconLoader
import com.skydream.androiddesk.app.home.SystemHome
import com.skydream.androiddesk.core.control.AgentAccess
import com.skydream.androiddesk.core.control.ConfirmTier
import com.skydream.androiddesk.core.control.DeskCommand
import com.skydream.androiddesk.core.home.HomeRoleState
import com.skydream.androiddesk.core.icon.IconStyle
import com.skydream.androiddesk.core.icon.IconStyleOverride
import com.skydream.androiddesk.core.icon.IconTheme
import com.skydream.androiddesk.core.layout.CellPosition
import com.skydream.androiddesk.core.layout.DeskItem
import com.skydream.androiddesk.core.layout.DesktopLayout
import com.skydream.androiddesk.core.layout.DockSlot
import com.skydream.androiddesk.core.layout.GridDensity
import com.skydream.androiddesk.core.layout.GridSpec
import com.skydream.androiddesk.core.layout.appKey
import com.skydream.androiddesk.core.layout.gridSpecFor
import com.skydream.androiddesk.core.state.DeskSettings
import com.skydream.androiddesk.core.state.DeskSnapshot
import com.skydream.androiddesk.sdk.DeskProtocol.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Screen {
    Desktop,
    Drawer,
    AppManager,
    IconStyle,
    Settings,
}

data class DeskUiState(
    val layout: DesktopLayout,
    /** False until the saved layout and the installed-app list are both in. */
    val ready: Boolean = false,
    val iconTheme: IconTheme = IconTheme(),
    val settings: DeskSettings = DeskSettings(),
    val apps: List<AppEntry> = emptyList(),
    val currentPage: Int = 0,
    val screen: Screen = Screen.Desktop,
    val homeRole: HomeRoleState = HomeRoleState.preview(),
    /** App the assistant just acted on; its icon is ringed for a moment. */
    val highlightKey: String? = null,
) {
    val appsByKey: Map<String, AppEntry> by lazy { apps.associateBy { it.key } }
    val appsByPackage: Map<String, List<AppEntry>> by lazy { apps.groupBy { it.packageName } }
    val drawerApps: List<AppEntry> by lazy { apps.filterNot { it.key in settings.hiddenApps } }

    fun entryFor(packageName: String?, activityName: String?): AppEntry? {
        packageName ?: return null
        return appsByKey[appKey(packageName, activityName)] ?: appsByPackage[packageName]?.firstOrNull()
    }

    fun entryFor(item: DeskItem): AppEntry? = entryFor(item.packageName, item.activityName)
    fun entryFor(slot: DockSlot): AppEntry? = entryFor(slot.packageName, slot.activityName)

    fun isOnDesktop(entry: AppEntry): Boolean = layout.hasApp(entry.packageName, entry.activityName)
    fun isInDock(entry: AppEntry): Boolean = layout.dockHasApp(entry.packageName, entry.activityName)
    fun isHidden(entry: AppEntry): Boolean = entry.key in settings.hiddenApps
    fun styleFor(entry: AppEntry): IconStyle = iconTheme.styleFor(entry.key)
}

/** What the controller found when an assistant asked for something, before anyone is asked to confirm. */
sealed interface Prepared {
    data class Refused(val message: String) : Prepared
    class Ready(val description: String, val run: () -> AgentReply) : Prepared
}

/**
 * The one owner of desktop state. The user's touches and an assistant's requests both end up
 * in the same methods here, which is what lets the two work on the same desktop at once.
 */
@OptIn(FlowPreview::class)
class DeskController(private val appContext: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val catalog = AppCatalog(appContext)
    val icons = IconLoader(appContext, catalog)
    val home = SystemHome(appContext)
    private val store = DeskStore(appContext)

    val debuggable: Boolean = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val _state = MutableStateFlow(DeskUiState(layout = DesktopLayout.empty(GridSpec(4, 5, 4))))
    val state: StateFlow<DeskUiState> = _state.asStateFlow()

    val agent = AgentHub(this)

    private var loaded = false
    private var shortSideDp = 0
    private var longSideDp = 0
    private var highlightJob: Job? = null

    init {
        catalog.onPackageChanged = { icons.invalidate(it) }
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { store.load() }
            if (snapshot != null) {
                _state.update {
                    it.copy(layout = snapshot.layout, iconTheme = snapshot.iconTheme, settings = snapshot.settings)
                }
            }
            loaded = true
            reconcile()
            catalog.start(scope)
            scope.launch {
                catalog.apps.filterNotNull().collect { apps ->
                    _state.update { it.copy(apps = apps) }
                    reconcile()
                }
            }
            scope.launch {
                _state.filter { it.ready }
                    .map { DeskSnapshot(it.layout, it.iconTheme, it.settings) }
                    .distinctUntilChanged()
                    .debounce(300)
                    .collect { snapshot -> withContext(Dispatchers.IO) { store.save(snapshot) } }
            }
        }
        refreshHomeRole()
    }

    // ---- Lifecycle inputs from the activity ----

    fun onScreenMetrics(shortSideDp: Int, longSideDp: Int) {
        if (shortSideDp <= 0 || longSideDp <= 0) return
        this.shortSideDp = shortSideDp
        this.longSideDp = longSideDp
        reconcile()
    }

    fun refreshHomeRole() {
        val role = if (home.isDefaultHome()) HomeRoleState.defaultHome() else HomeRoleState.preview()
        _state.update { if (it.homeRole == role) it else it.copy(homeRole = role) }
    }

    /** Home pressed. [alreadyOnDesk] is true when the desktop was already in front. */
    fun onHomePressed(alreadyOnDesk: Boolean) {
        _state.update { it.copy(screen = Screen.Desktop, currentPage = if (alreadyOnDesk) 0 else it.currentPage) }
    }

    /**
     * Brings saved state in line with reality: the grid for this screen, apps that are gone,
     * and the first-run starter icons. Never runs before the saved layout is loaded, and
     * never prunes icons before the installed-app list has actually arrived.
     */
    private fun reconcile() {
        if (!loaded || shortSideDp == 0) return
        val appsLoaded = catalog.apps.value != null
        _state.update { s ->
            var layout = s.layout.withGrid(gridSpecFor(shortSideDp, longSideDp, s.settings.density))
            var theme = s.iconTheme
            var settings = s.settings
            if (appsLoaded && s.apps.isNotEmpty()) {
                val keys = s.appsByKey.keys
                val packages = s.appsByPackage.keys
                layout = layout.retainApps { pkg, activity ->
                    if (activity == null) pkg in packages else appKey(pkg, activity) in keys
                }
                theme = theme.pruned(keys)
                val hidden = settings.hiddenApps intersect keys
                if (hidden.size != settings.hiddenApps.size) settings = settings.copy(hiddenApps = hidden)
                if (!settings.seeded) {
                    layout = withStarterApps(layout, s)
                    settings = settings.copy(seeded = true)
                }
            }
            s.copy(
                layout = layout,
                iconTheme = theme,
                settings = settings,
                ready = appsLoaded,
                currentPage = s.currentPage.coerceIn(0, layout.pageCount - 1),
            )
        }
    }

    /** First run only: a few real apps the system resolves for common jobs. No placeholders. */
    private fun withStarterApps(layout: DesktopLayout, s: DeskUiState): DesktopLayout {
        val pm = appContext.packageManager

        fun resolve(intent: Intent, flags: Int = PackageManager.MATCH_DEFAULT_ONLY): AppEntry? {
            val pkg = try {
                pm.resolveActivity(intent, flags)?.activityInfo?.packageName
            } catch (_: Exception) {
                null
            }
            // "android" is the system's app chooser, i.e. no single default exists.
            return if (pkg == null || pkg == "android") null else s.appsByPackage[pkg]?.firstOrNull()
        }

        fun category(name: String): AppEntry? = resolve(Intent(Intent.ACTION_MAIN).addCategory(name), 0)

        val dock = listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinctBy { it.key }.take(layout.grid.dockSlots)

        val page = listOfNotNull(
            resolve(Intent(Settings.ACTION_SETTINGS)),
            category(Intent.CATEGORY_APP_GALLERY),
            category(Intent.CATEGORY_APP_CALENDAR),
            category(Intent.CATEGORY_APP_CONTACTS),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) category(Intent.CATEGORY_APP_FILES) else null,
            category(Intent.CATEGORY_APP_MUSIC),
            category(Intent.CATEGORY_APP_CALCULATOR),
            category(Intent.CATEGORY_APP_MAPS),
            category(Intent.CATEGORY_APP_EMAIL),
            category(Intent.CATEGORY_APP_MARKET),
        ).distinctBy { it.key }.filterNot { it in dock }.take(8)

        var next = layout
        dock.forEach { next = next.pinAppToDock(it.packageName, it.activityName) }
        page.forEach { next = next.placeApp(it.packageName, it.activityName, it.label) }
        return next
    }

    // ---- Navigation ----

    fun openScreen(screen: Screen) = _state.update { it.copy(screen = screen) }

    /** Handles Back. Returns false when there is nothing of ours to close. */
    fun back(): Boolean {
        val current = _state.value.screen
        if (current == Screen.Desktop) return false
        // Icon styles and app management are opened from more than one place; Back always
        // lands on the desktop so it can never trap the user in a loop of pages.
        _state.update { it.copy(screen = Screen.Desktop) }
        return true
    }

    fun setPage(page: Int) = _state.update {
        if (page in 0 until it.layout.pageCount) it.copy(currentPage = page) else it
    }

    fun onPageSettled(page: Int) = _state.update {
        if (page != it.currentPage && page in 0 until it.layout.pageCount) it.copy(currentPage = page) else it
    }

    // ---- Apps ----

    fun launch(entry: AppEntry): Boolean {
        val ok = catalog.launch(entry)
        if (!ok) toast("打不开「${entry.label}」")
        return ok
    }

    fun showAppDetails(entry: AppEntry) {
        if (!catalog.showDetails(entry)) toast("打不开应用信息")
    }

    /** Returns the 0-based page the icon is on afterwards, or null if it could not be placed. */
    fun addToDesktop(entry: AppEntry, page: Int? = null, announce: Boolean = true): Int? {
        var landed: Int? = null
        _state.update { s ->
            val layout = s.layout.placeApp(entry.packageName, entry.activityName, entry.label, page ?: s.currentPage)
            landed = layout.appItem(entry.packageName, entry.activityName)?.position?.pageIndex
            s.copy(layout = layout)
        }
        if (announce) landed?.let { toast("「${entry.label}」已放到桌面第 ${it + 1} 页") }
        return landed
    }

    fun removeFromDesktop(entry: AppEntry) = _state.update { s ->
        val layout = s.layout.removeApp(entry.packageName, entry.activityName).trimTrailingEmptyPages()
        s.copy(layout = layout, currentPage = s.currentPage.coerceIn(0, layout.pageCount - 1))
    }

    fun removeItem(itemId: String) = _state.update { it.copy(layout = it.layout.removeItem(itemId)) }

    /** Returns false when the target cell cannot take the icon. */
    fun moveItem(itemId: String, to: CellPosition): Boolean {
        var moved = false
        _state.update { s ->
            val layout = s.layout.moveItem(itemId, to)
            moved = layout !== s.layout
            s.copy(layout = layout)
        }
        return moved
    }

    /** Adds a blank page at the end (used while dragging an icon past the last page). */
    fun addPage(): Int {
        _state.update { it.copy(layout = it.layout.addPage()) }
        return _state.value.layout.pageCount - 1
    }

    fun trimPages() = _state.update { s ->
        val layout = s.layout.trimTrailingEmptyPages()
        if (layout === s.layout) s else s.copy(layout = layout, currentPage = s.currentPage.coerceIn(0, layout.pageCount - 1))
    }

    fun pinToDock(entry: AppEntry, announce: Boolean = true): Boolean {
        val before = _state.value.layout
        if (before.dockHasApp(entry.packageName, entry.activityName)) return true
        if (before.dockIsFull) {
            if (announce) toast("底栏已满，先拿走一个再钉")
            return false
        }
        _state.update { it.copy(layout = it.layout.pinAppToDock(entry.packageName, entry.activityName)) }
        return true
    }

    fun unpinFromDock(packageName: String, activityName: String?) = _state.update {
        it.copy(layout = it.layout.unpinAppFromDock(packageName, activityName))
    }

    fun setHidden(entry: AppEntry, hidden: Boolean) = updateSettings {
        it.copy(hiddenApps = if (hidden) it.hiddenApps + entry.key else it.hiddenApps - entry.key)
    }

    // ---- Look ----

    fun setGlobalIconStyle(style: IconStyle) = _state.update { it.copy(iconTheme = it.iconTheme.withGlobal(style)) }

    fun setIconOverride(appKey: String, override: IconStyleOverride?) = _state.update {
        it.copy(iconTheme = it.iconTheme.withOverride(appKey, override))
    }

    fun clearIconOverrides() = _state.update { it.copy(iconTheme = it.iconTheme.copy(overrides = emptyMap())) }

    fun setDensity(density: GridDensity) {
        updateSettings { it.copy(density = density) }
        reconcile()
    }

    fun setPetPosition(x: Float, y: Float) = updateSettings {
        it.copy(petX = x.coerceIn(0f, 1f), petY = y.coerceIn(0f, 1f))
    }

    fun rememberSystemLauncher(id: String?) = updateSettings { it.copy(systemLauncher = id) }

    // ---- Assistant permissions (only the user changes these) ----

    fun setAgentAllowed(packageName: String, allowed: Boolean) = updateAccess {
        it.copy(allowedPackages = if (allowed) it.allowedPackages + packageName else it.allowedPackages - packageName)
    }

    fun setConfirmTier(tier: ConfirmTier) = updateAccess { it.copy(tier = tier) }

    fun setAdbAllowed(allowed: Boolean) = updateAccess { it.copy(allowAdbShell = allowed && debuggable) }

    private fun updateAccess(change: (AgentAccess) -> AgentAccess) = updateSettings {
        it.copy(agentAccess = change(it.agentAccess))
    }

    private fun updateSettings(change: (DeskSettings) -> DeskSettings) = _state.update {
        it.copy(settings = change(it.settings))
    }

    /** Name shown for whoever is calling the assistant entry point. */
    fun agentLabel(packageName: String): String {
        if (packageName == AgentAccess.ADB_SHELL_PACKAGE) return "ADB 调试"
        _state.value.appsByPackage[packageName]?.firstOrNull()?.let { return it.label }
        return try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun toast(text: String) {
        scope.launch { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
    }

    private fun highlight(key: String) {
        highlightJob?.cancel()
        _state.update { it.copy(highlightKey = key) }
        highlightJob = scope.launch {
            delay(1800)
            _state.update { if (it.highlightKey == key) it.copy(highlightKey = null) else it }
        }
    }

    // ---- Assistant requests ----

    /**
     * Checks an assistant's request against the current desktop and returns what would be
     * done, worded for the user. Nothing changes until [Prepared.Ready.run] is called, which
     * the hub only does after the confirm gate has let the request through. Main thread only.
     */
    fun prepare(command: DeskCommand): Prepared {
        val s = _state.value
        fun app(pkg: String): AppEntry? = s.appsByPackage[pkg]?.firstOrNull()
        fun unknown(pkg: String) = Prepared.Refused("没有找到应用：$pkg")
        fun ok(message: String, data: String? = null) = AgentReply(Status.OK, message, data)
        fun pageText(index: Int) = "第 ${index + 1} 页"

        return when (command) {
            is DeskCommand.LaunchApp -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                Prepared.Ready("打开「${entry.label}」") {
                    highlight(entry.key)
                    if (catalog.launch(entry)) ok("已打开「${entry.label}」")
                    else AgentReply(Status.INVALID, "打不开「${entry.label}」")
                }
            }

            is DeskCommand.GoToPage -> {
                if (command.pageIndex !in 0 until s.layout.pageCount) {
                    return Prepared.Refused("没有${pageText(command.pageIndex)}，桌面一共 ${s.layout.pageCount} 页")
                }
                Prepared.Ready("翻到${pageText(command.pageIndex)}") {
                    setPage(command.pageIndex)
                    ok("已翻到${pageText(command.pageIndex)}")
                }
            }

            DeskCommand.OpenDrawer -> {
                if (s.screen != Screen.Desktop && s.screen != Screen.Drawer) {
                    return Prepared.Refused("用户正在使用桌面的设置页，现在不能打开抽屉")
                }
                Prepared.Ready("打开应用抽屉") {
                    openScreen(Screen.Drawer)
                    ok("已打开应用抽屉")
                }
            }

            DeskCommand.CloseDrawer -> Prepared.Ready("关上应用抽屉") {
                if (_state.value.screen == Screen.Drawer) openScreen(Screen.Desktop)
                ok("应用抽屉已关上")
            }

            is DeskCommand.AddToDesktop -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                val page = command.pageIndex
                if (page != null && page >= s.layout.pageCount) {
                    return Prepared.Refused("没有${pageText(page)}，桌面一共 ${s.layout.pageCount} 页")
                }
                Prepared.Ready("把「${entry.label}」放到桌面") {
                    if (_state.value.isOnDesktop(entry)) {
                        ok("「${entry.label}」已经在桌面上")
                    } else {
                        val landed = addToDesktop(entry, page, announce = false)
                        if (landed == null) {
                            AgentReply(Status.INVALID, "桌面放不下「${entry.label}」")
                        } else {
                            setPage(landed)
                            highlight(entry.key)
                            ok("已把「${entry.label}」放到桌面${pageText(landed)}")
                        }
                    }
                }
            }

            is DeskCommand.RemoveFromDesktop -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                Prepared.Ready("把「${entry.label}」从桌面拿走（不卸载）") {
                    if (!_state.value.isOnDesktop(entry)) {
                        ok("「${entry.label}」本来就不在桌面上")
                    } else {
                        removeFromDesktop(entry)
                        ok("已把「${entry.label}」从桌面拿走")
                    }
                }
            }

            is DeskCommand.MoveItem -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                val item = s.layout.appItem(entry.packageName, entry.activityName)
                    ?: return Prepared.Refused("「${entry.label}」不在桌面上，先用 addToDesktop 放上来")
                val target = CellPosition(command.pageIndex, command.cellX, command.cellY)
                if (target.pageIndex >= s.layout.pageCount || !target.fits(s.layout.grid.columns, s.layout.grid.rows)) {
                    return Prepared.Refused(
                        "位置超出桌面：共 ${s.layout.pageCount} 页，每页 ${s.layout.grid.columns} 列 ${s.layout.grid.rows} 行",
                    )
                }
                Prepared.Ready("把「${entry.label}」移到${pageText(target.pageIndex)}第 ${target.cellY + 1} 行第 ${target.cellX + 1} 列") {
                    if (moveItem(item.id, target) || _state.value.layout.item(item.id)?.position == target) {
                        setPage(target.pageIndex)
                        highlight(entry.key)
                        ok("已移动「${entry.label}」")
                    } else {
                        AgentReply(Status.INVALID, "那个位置放不下")
                    }
                }
            }

            is DeskCommand.PinToDock -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                if (!s.isInDock(entry) && s.layout.dockIsFull) return Prepared.Refused("底栏已满")
                Prepared.Ready("把「${entry.label}」钉到底栏") {
                    if (pinToDock(entry, announce = false)) ok("「${entry.label}」已在底栏")
                    else AgentReply(Status.INVALID, "底栏已满")
                }
            }

            is DeskCommand.UnpinFromDock -> {
                val entry = app(command.packageName) ?: return unknown(command.packageName)
                Prepared.Ready("把「${entry.label}」从底栏拿走") {
                    unpinFromDock(entry.packageName, entry.activityName)
                    ok("「${entry.label}」已不在底栏")
                }
            }

            is DeskCommand.SetIconStyle -> {
                val look = listOfNotNull(command.shape?.label, command.treatment?.label).joinToString(" · ")
                val pkg = command.packageName
                if (pkg == null) {
                    Prepared.Ready("把所有图标换成「$look」") {
                        val global = _state.value.iconTheme.global
                        setGlobalIconStyle(
                            global.copy(
                                shape = command.shape ?: global.shape,
                                treatment = command.treatment ?: global.treatment,
                            ),
                        )
                        ok("图标已换成「$look」")
                    }
                } else {
                    val entries = s.appsByPackage[pkg].orEmpty()
                    if (entries.isEmpty()) return unknown(pkg)
                    Prepared.Ready("把「${entries.first().label}」的图标换成「$look」") {
                        entries.forEach { entry ->
                            val current = _state.value.iconTheme.overrideFor(entry.key)
                            setIconOverride(
                                entry.key,
                                IconStyleOverride(
                                    shape = command.shape ?: current.shape,
                                    treatment = command.treatment ?: current.treatment,
                                ),
                            )
                        }
                        highlight(entries.first().key)
                        ok("「${entries.first().label}」的图标已换成「$look」")
                    }
                }
            }

            DeskCommand.ReadDesktop -> Prepared.Ready("查看桌面布局") { ok("桌面布局", desktopJson(_state.value)) }
            DeskCommand.ListApps -> Prepared.Ready("查看应用列表") { ok("应用列表", appsJson(_state.value)) }

            // Status reports and task end are handled by the hub; they never reach the desktop state.
            is DeskCommand.ReportStatus, DeskCommand.EndTask -> Prepared.Ready("") { ok("") }
        }
    }

    private fun desktopJson(s: DeskUiState): String = JSONObject().apply {
        put("pageCount", s.layout.pageCount)
        put("currentPage", s.currentPage)
        put("columns", s.layout.grid.columns)
        put("rows", s.layout.grid.rows)
        put("drawerOpen", s.screen == Screen.Drawer)
        put("iconShape", s.iconTheme.global.shape.name)
        put("iconTreatment", s.iconTheme.global.treatment.name)
        put(
            "items",
            JSONArray().apply {
                s.layout.items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("package", item.packageName)
                            put("label", s.entryFor(item)?.label ?: item.label.orEmpty())
                            put("page", item.position.pageIndex)
                            put("x", item.position.cellX)
                            put("y", item.position.cellY)
                        },
                    )
                }
            },
        )
        put(
            "dock",
            JSONArray().apply {
                s.layout.dock.filterNot { it.isEmpty }.forEach { slot ->
                    put(
                        JSONObject().apply {
                            put("slot", slot.index)
                            put("package", slot.packageName)
                            put("label", s.entryFor(slot)?.label.orEmpty())
                        },
                    )
                }
            },
        )
    }.toString()

    /** Apps the user hid from the drawer are left out here too. */
    private fun appsJson(s: DeskUiState): String = JSONArray().apply {
        s.drawerApps.forEach { entry ->
            put(
                JSONObject().apply {
                    put("package", entry.packageName)
                    put("label", entry.label)
                    put("onDesktop", s.isOnDesktop(entry))
                    put("inDock", s.isInDock(entry))
                },
            )
        }
    }.toString()
}
