package com.skydream.androiddesk.app.home

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Another home-screen app installed on this device. */
data class LauncherChoice(
    val component: ComponentName,
    val label: String,
) {
    val id: String get() = component.flattenToString()
}

/**
 * Everything about being (or not being) the device's home screen, and about getting back to
 * the home screen the user had before.
 */
class SystemHome(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** True only when the system would really bring this app up for Home. Never guessed. */
    fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val resolved = packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The system's own "choose your home app" prompt, or null where only the settings page exists. */
    fun roleRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** System settings page where the default home app is chosen. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /** Other home-screen apps on the device, i.e. the "normal desktop" candidates. */
    fun otherLaunchers(): List<LauncherChoice> = try {
        packageManager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            // The settings app registers a placeholder home used only while the device is locked.
            .filterNot { it.packageName == "com.android.settings" }
            .map {
                LauncherChoice(
                    component = ComponentName(it.packageName, it.name),
                    label = it.loadLabel(packageManager)?.toString()?.trim().orEmpty().ifEmpty { it.packageName },
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.label }
            .toList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Brings another home-screen app to the front without changing which one is the default.
     * Pressing Home afterwards still follows the system's default-home setting.
     */
    fun intentFor(choice: LauncherChoice): Intent = homeIntent()
        .setComponent(choice.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}
