package com.skydream.androiddesk.app

import android.content.Context

/**
 * Process-wide owner of the desktop's state. The home activity and the assistant entry point
 * (a ContentProvider, created before any activity) must see the same controller.
 */
object DeskGraph {
    @Volatile
    private var controller: DeskController? = null

    fun controller(context: Context): DeskController =
        controller ?: synchronized(this) {
            controller ?: DeskController(context.applicationContext).also { controller = it }
        }
}
