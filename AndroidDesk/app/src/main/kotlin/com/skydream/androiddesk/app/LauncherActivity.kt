package com.skydream.androiddesk.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.skydream.androiddesk.app.ui.DeskRoot

class LauncherActivity : ComponentActivity() {
    private val controller by lazy { DeskGraph.controller(this) }
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        controller.refreshHomeRole()
        if (!controller.state.value.homeRole.isDefaultHome) {
            controller.toast("还没有改过来。你可以随时在桌面设置里再试。")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        updateMetrics(resources.configuration)
        setContent {
            DeskRoot(controller, onRequestDefaultHome = ::requestDefaultHome) { light ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshHomeRole()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            controller.onHomePressed(
                hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0,
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateMetrics(newConfig)
    }

    private fun updateMetrics(config: Configuration) {
        val width = config.screenWidthDp
        val height = config.screenHeightDp
        controller.onScreenMetrics(minOf(width, height), maxOf(width, height))
    }

    private fun requestDefaultHome() {
        val home = controller.home
        val intent = home.roleRequestIntent() ?: home.homeSettingsIntent()
        runCatching { homeRoleRequest.launch(intent) }
            .onFailure { controller.toast("还没有改过来。你可以随时在桌面设置里再试。") }
    }
}
