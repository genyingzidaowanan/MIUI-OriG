package com.redwind.hyperorig

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.redwind.hyperorig.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val openDetail = intent.getBooleanExtra("open_detail", false)
        val prefs = getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE)
        val themeModeValue = prefs.getInt("theme_mode", 0)
        val isDark = when (themeModeValue) {
            1 -> false
            2 -> true
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val bgColor = if (isDark) Color.BLACK else Color.WHITE
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
        window.decorView.setBackgroundColor(bgColor)

        setContent {
            val prefs = remember { getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE) }
            val themeMode = remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }
            val systemDark = isSystemInDarkTheme()
            val darkMode = when (themeMode.value) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            DisposableEffect(darkMode) {
                window.navigationBarColor = Color.TRANSPARENT
                window.statusBarColor = Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
                // 状态栏/导航栏图标跟随主题明暗，避免深色背景下图标看不清
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkMode
                controller.isAppearanceLightNavigationBars = !darkMode
                onDispose {}
            }

            App(
                themeMode = themeMode,
                onThemeModeChange = {
                    themeMode.value = it
                    prefs.edit().putInt("theme_mode", it).apply()
                },
                openDetail = openDetail
            )
        }
    }
}
