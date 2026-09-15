package com.redwind.hyperorig.utils

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * 运行日志的等级开关（位掩码，可多选）。
 *
 * 默认**关闭**。打开后默认记录 Warn + Error。
 * 关闭或过滤掉某等级时，[RuntimeLog] 会在做字符串拼接与跨进程广播之前就直接返回，
 * 从而减少日志带来的耗电与性能开销。
 *
 * 注意：必须同时写入 App 本地 prefs 与 LSPosed remote prefs，
 * 否则被注入的进程（蓝牙、MiLink 等）读不到设置，过滤就会失效。
 */
object LogLevels {
    const val PREFS_NAME = "hyperorig_settings"
    const val KEY = "runtime_log_levels"

    const val INFO = 1
    const val DEBUG = 2
    const val WARN = 4
    const val ERROR = 8
    const val ALL = INFO or DEBUG or WARN or ERROR

    /** 默认关闭日志 */
    const val OFF = 0
    const val DEFAULT = OFF

    /** 打开日志时的默认等级 */
    const val DEFAULT_ON = WARN or ERROR

    @Volatile
    private var cached: Int = DEFAULT

    fun current(): Int = cached

    fun isEnabled(level: Int): Boolean = (cached and level) != 0

    fun isOn(): Boolean = cached != OFF

    /** 运行日志里的等级字符 -> 位掩码 */
    fun levelOf(c: Char): Int = when (c.uppercaseChar()) {
        'E' -> ERROR
        'W' -> WARN
        'D' -> DEBUG
        else -> INFO
    }

    fun label(mask: Int): String {
        val parts = buildList {
            if (mask and INFO != 0) add("Info")
            if (mask and DEBUG != 0) add("Debug")
            if (mask and WARN != 0) add("Warn")
            if (mask and ERROR != 0) add("Error")
        }
        return if (parts.isEmpty()) "已关闭" else parts.joinToString(" · ")
    }

    fun refresh(prefs: SharedPreferences?) {
        cached = read(prefs)
    }

    fun read(prefs: SharedPreferences?): Int {
        val value = prefs?.getInt(KEY, DEFAULT) ?: DEFAULT
        return value and ALL
    }

    /**
     * 写入等级设置：同时写本地与 remote prefs，并清空旧日志，
     * 这样用户切换后能立刻看到过滤效果（不会混有旧级别的历史内容）。
     */
    fun write(prefs: SharedPreferences?, service: XposedService?, mask: Int) {
        val value = mask and ALL
        cached = value
        runCatching {
            prefs?.edit()?.putInt(KEY, value)?.apply()
        }
        runCatching {
            service?.getRemotePreferences(PREFS_NAME)?.edit()?.putInt(KEY, value)?.apply()
        }
        RuntimeLog.clear()
    }
}
