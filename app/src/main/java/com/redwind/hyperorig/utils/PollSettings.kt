package com.redwind.hyperorig.utils

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * 耳机状态「定时轮询」间隔设置。
 *
 * 模块会定时向耳机下发一批查询指令（电量 / 降噪 / 抗风噪 / EQ …）。
 * 间隔越长越省电；设为 [OFF] 则完全关闭定时轮询，只依赖耳机异步上报。
 *
 * 与 [LogLevels] 一样，必须同时写本地 prefs 与 LSPosed remote prefs，
 * 否则被注入的进程读不到设置。
 */
object PollSettings {
    const val PREFS_NAME = "hyperorig_settings"
    const val KEY = "poll_interval_ms"

    /** 关闭定时轮询（仅依赖耳机异步上报） */
    const val OFF = 0
    const val DEFAULT = 30_000

    val OPTIONS = listOf(30_000, 60_000, 120_000, OFF)
    val LABELS = listOf("30 秒", "60 秒", "120 秒", "关闭")

    @Volatile
    private var cached: Int = DEFAULT

    fun current(): Int = cached

    fun labelOf(value: Int): String = when (value) {
        60_000 -> "60 秒"
        120_000 -> "120 秒"
        OFF -> "关闭（仅依赖耳机异步上报）"
        else -> "30 秒"
    }

    fun indexOf(value: Int): Int = OPTIONS.indexOf(value).takeIf { it >= 0 } ?: 0

    fun refresh(prefs: SharedPreferences?) {
        cached = read(prefs)
    }

    fun read(prefs: SharedPreferences?): Int {
        val value = prefs?.getInt(KEY, DEFAULT) ?: DEFAULT
        return if (value in OPTIONS) value else DEFAULT
    }

    fun write(prefs: SharedPreferences?, service: XposedService?, value: Int) {
        val v = if (value in OPTIONS) value else DEFAULT
        cached = v
        runCatching { prefs?.edit()?.putInt(KEY, v)?.apply() }
        runCatching { service?.getRemotePreferences(PREFS_NAME)?.edit()?.putInt(KEY, v)?.apply() }
    }
}
