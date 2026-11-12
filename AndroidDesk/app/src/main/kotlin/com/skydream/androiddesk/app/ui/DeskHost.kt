package com.skydream.androiddesk.app.ui

import com.skydream.androiddesk.app.data.AppEntry
import com.skydream.androiddesk.app.home.LauncherChoice

/** Things only the activity can do: system prompts, other activities, system bar appearance. */
interface DeskHost {
    fun requestDefaultHome()
    fun openHomeSettings()
    fun openWallpaperPicker()
    fun uninstall(entry: AppEntry)
    fun openLauncher(choice: LauncherChoice)
    fun setLightSystemBars(light: Boolean)
}
