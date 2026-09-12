package com.redwind.hyperorig.utils

import android.content.SharedPreferences

/**
 * 记住用户上次使用的“降噪子模式”（3=普通 / 4=深度 / 5=实验性）。
 *
 * 这样在 “关闭 → 降噪” 或 “通透 → 降噪” 切换时能恢复上次的档位，
 * 而不是永远回到“普通降噪”。
 */
object AncModeMemory {
    const val PREFS_NAME = "hyperorig_settings"
    const val KEY_LAST_NC_MODE = "last_nc_mode"
    const val DEFAULT_NC_MODE = 3

    fun isValid(mode: Int): Boolean = mode in MIN_MODE..MAX_MODE

    fun read(prefs: SharedPreferences?): Int {
        val value = prefs?.getInt(KEY_LAST_NC_MODE, DEFAULT_NC_MODE) ?: DEFAULT_NC_MODE
        return if (isValid(value)) value else DEFAULT_NC_MODE
    }

    fun write(prefs: SharedPreferences?, mode: Int) {
        if (prefs == null || !isValid(mode)) return
        runCatching { prefs.edit().putInt(KEY_LAST_NC_MODE, mode).apply() }
    }

    private const val MIN_MODE = 3
    private const val MAX_MODE = 5
}
