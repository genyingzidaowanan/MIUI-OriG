package com.redwind.hyperorig.utils

import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 跨进程运行时日志。
 *
 * - 在被 LSPosed 注入的任意进程（App / com.android.bluetooth / com.milink.service / com.android.settings / ...）
 *   里通过 [i]/[d]/[w]/[e] 记录；
 * - 非 App 进程会通过定向广播把日志转发到模块 App；
 * - App 进程收到后写入私有文件 `files/runtime.log`，界面可查看、复制、分享导出。
 *
 * 设计目标：无需 root、无需 LSPosed 管理器，用户直接在 App 内一键导出日志。
 */
object RuntimeLog {
    const val ACTION_RUNTIME_LOG = "com.redwind.hyperorig.action.runtime_log"
    const val EXTRA_LINE = "line"
    const val APP_PACKAGE = "com.redwind.hyperorig"
    const val FILE_NAME = "runtime.log"

    private const val MAX_MEMORY = 5000
    private const val MAX_FILE_BYTES = 512 * 1024L

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "HyperOriG-LogWriter").apply { isDaemon = true }
    }

    @Volatile
    private var ctx: Context? = null

    @Volatile
    private var isAppProcess = false

    @Volatile
    private var crashHandlerInstalled = false

    /** 在进程启动时调用；App 进程会载入已有落盘日志。 */
    fun init(context: Context) {
        ctx = context.applicationContext
        isAppProcess = context.packageName == APP_PACKAGE
        if (isAppProcess) {
            runCatching {
                val f = logFile(context)
                if (f.exists()) {
                    val existing = f.readLines()
                    synchronized(lines) {
                        lines.clear()
                        lines.addAll(existing.takeLast(MAX_MEMORY))
                    }
                }
            }
        }
    }

    fun i(tag: String, message: String) = record('I', tag, message)
    fun d(tag: String, message: String) = record('D', tag, message)
    fun w(tag: String, message: String) = record('W', tag, message)
    fun e(tag: String, message: String) = record('E', tag, message)

    fun record(level: Char, tag: String, message: String) {
        val line = "${fmt.format(Date())} $level/$tag: $message"
        appendLocal(line)
        if (isAppProcess) persist(line) else forward(line)
    }

    /** App 进程收到其它进程转发过来的日志行。 */
    fun acceptForwarded(context: Context, line: String) {
        if (ctx == null) init(context)
        appendLocal(line)
        persist(line)
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    /** 安装全局未捕获异常处理器，把崩溃堆栈也写进运行日志。各进程各装一次。 */
    fun installCrashHandler(context: Context? = null) {
        if (context != null) init(context)
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val pkg = runCatching { resolveContext()?.packageName }.getOrNull() ?: "?"
                writeLineSync("E/CRASH: uncaught in $pkg thread=${thread.name}")
                writeLineSync("E/CRASH: ${throwable.javaClass.name}: ${throwable.message}")
                throwable.stackTrace.take(40).forEach { writeLineSync("E/CRASH:     at $it") }
                throwable.cause?.let { cause ->
                    writeLineSync("E/CRASH: Caused by ${cause.javaClass.name}: ${cause.message}")
                    cause.stackTrace.take(20).forEach { writeLineSync("E/CRASH:     at $it") }
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
        val c = ctx ?: resolveContext() ?: return
        writeExecutor.execute { runCatching { logFile(c).delete() } }
    }

    /** 把当前内存日志写入文件并返回（供分享/导出）。 */
    fun exportToFile(context: Context): File? {
        val f = runCatching { logFile(context) }.getOrNull() ?: return null
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(snapshot().joinToString("\n") + "\n")
        }
        return f
    }

    private fun appendLocal(line: String) {
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_MEMORY) lines.removeFirst()
        }
    }

    /** 崩溃时同步落盘（不走异步队列），保证堆栈不丢。 */
    private fun writeLineSync(line: String) {
        appendLocal(line)
        if (isAppProcess) {
            val c = ctx ?: resolveContext() ?: return
            runCatching {
                val f = logFile(c)
                f.parentFile?.mkdirs()
                f.appendText(line + "\n")
            }
        } else {
            forward(line)
        }
    }

    private fun forward(line: String) {
        val c = resolveContext() ?: return
        runCatching {
            c.sendBroadcast(Intent(ACTION_RUNTIME_LOG).apply {
                setPackage(APP_PACKAGE)
                putExtra(EXTRA_LINE, line)
            })
        }
    }

    private fun persist(line: String) {
        val c = ctx ?: resolveContext() ?: return
        writeExecutor.execute {
            runCatching {
                val f = logFile(c)
                f.parentFile?.mkdirs()
                if (f.exists() && f.length() > MAX_FILE_BYTES) {
                    val keep = f.readLines().takeLast(MAX_MEMORY / 2)
                    f.writeText(keep.joinToString("\n") + "\n")
                }
                f.appendText(line + "\n")
            }
        }
    }

    /**
     * 在未显式 [init] 的进程里（模块 Log 的调用方通常没有 Context）惰性获取 Application。
     */
    private fun resolveContext(): Context? {
        ctx?.let { return it }
        val c = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (c != null) {
            ctx = c
            isAppProcess = c.packageName == APP_PACKAGE
        }
        return c
    }

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
