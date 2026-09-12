package com.redwind.hyperorig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redwind.hyperorig.utils.RuntimeLog

/**
 * 接收其它进程（com.android.bluetooth / com.milink.service 等）转发过来的运行日志，
 * 落到 App 私有目录，供“运行日志”界面查看与导出。
 */
class LogForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != RuntimeLog.ACTION_RUNTIME_LOG) return
        val line = intent.getStringExtra(RuntimeLog.EXTRA_LINE) ?: return
        RuntimeLog.acceptForwarded(context.applicationContext, line)
    }
}
