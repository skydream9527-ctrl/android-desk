package com.skydream.androiddesk.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import com.skydream.androiddesk.core.layout.appKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** One launchable entry of an installed app, as the system reports it. */
data class AppEntry(
    val key: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * The installed-app list, read from the system's LauncherApps service and kept current as
 * apps are installed, updated and removed. Nothing is cached on disk and nothing leaves the
 * device. Only the current user's own apps are listed (no work profile yet).
 */
class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val user: UserHandle = Process.myUserHandle()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first query has finished, so "not loaded yet" is never mistaken for "no apps". */
    val apps: StateFlow<List<AppEntry>?> = _apps

    /** Called on the main thread when a package's icon or label may have changed. */
    var onPackageChanged: ((String) -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach(::changed)
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach(::changed)
        }
    }

    private fun changed(packageName: String) {
        onPackageChanged?.invoke(packageName)
        refreshRequests.trySend(Unit)
    }

    fun start(scope: CoroutineScope) {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch {
            for (request in refreshRequests) {
                val result = withContext(Dispatchers.Default) { query() }
                if (result != null) _apps.value = result
            }
        }
        refreshRequests.trySend(Unit)
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    /** Null when the system call failed; the previous list is kept in that case. */
    private fun query(): List<AppEntry>? = try {
        val collator = Collator.getInstance(Locale.CHINA)
        launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val pkg = info.componentName.packageName
                val cls = info.componentName.className
                AppEntry(
                    key = appKey(pkg, cls),
                    packageName = pkg,
                    activityName = cls,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { pkg },
                    isSystem = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    } catch (_: Exception) {
        null
    }

    fun activityInfo(entry: AppEntry): LauncherActivityInfo? = try {
        launcherApps.getActivityList(entry.packageName, user)
            .firstOrNull { it.componentName.className == entry.activityName }
    } catch (_: Exception) {
        null
    }

    fun launch(entry: AppEntry, sourceBounds: Rect? = null): Boolean = try {
        launcherApps.startMainActivity(ComponentName(entry.packageName, entry.activityName), user, sourceBounds, null)
        true
    } catch (_: Exception) {
        false
    }

    fun showDetails(entry: AppEntry): Boolean = try {
        launcherApps.startAppDetailsActivity(ComponentName(entry.packageName, entry.activityName), user, null, null)
        true
    } catch (_: Exception) {
        false
    }
}
