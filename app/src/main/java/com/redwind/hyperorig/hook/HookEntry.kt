package com.redwind.hyperorig.hook

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import com.redwind.hyperorig.config.ConfigManager
import com.redwind.hyperorig.utils.LogLevels
import com.redwind.hyperorig.utils.PollSettings
import com.redwind.hyperorig.utils.RuntimeLog

class HookEntry : XposedModule() {
    private val TAG = "HyperOriG-HookEntry"
    private val configListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        when (param.packageName) {
            "com.android.systemui" -> {
                loadHook(SystemUIPluginHook, param.defaultClassLoader, param.packageName)
            }
            "com.android.bluetooth" -> {
                loadHook(HeadsetStateDispatcher, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook, param.defaultClassLoader, param.packageName)
            }
            "com.milink.service" -> {
                loadHook(MiLinkServiceHook, param.defaultClassLoader, param.packageName)
            }
            "com.xiaomi.bluetooth" -> {
                loadHook(MiBluetoothToastHook, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook, param.defaultClassLoader, param.packageName)
                loadHook(MoreSettingsRedirectHook, param.defaultClassLoader, param.packageName)
            }
            "com.android.settings" -> {
                loadHook(SettingsHeadsetHook, param.defaultClassLoader, param.packageName)
            }
        }
    }

    private fun loadHook(hook: HookContext, classLoader: ClassLoader, packageName: String) {
        Log.module = this
        RuntimeLog.installCrashHandler()
        hook.module = this
        hook.appClassLoader = classLoader
        hook.packageName = packageName
        hook.prefs = getRemotePreferences("hyperorig_settings")
        Log.d(TAG, "loadHook package=$packageName hook=${hook.javaClass.simpleName}")
        ConfigManager.init(hook.prefs)
        LogLevels.refresh(hook.prefs)
        PollSettings.refresh(hook.prefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(sharedPreferences)
            }
            if (key == null || key == LogLevels.KEY) {
                LogLevels.refresh(sharedPreferences)
            }
            if (key == null || key == PollSettings.KEY) {
                PollSettings.refresh(sharedPreferences)
            }
        }
        configListeners.add(configListener)
        hook.prefs.registerOnSharedPreferenceChangeListener(configListener)
        hook.onHook()
    }
}
