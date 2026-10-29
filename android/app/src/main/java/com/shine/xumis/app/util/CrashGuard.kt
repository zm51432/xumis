package com.shine.xumis.app.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import com.shine.xumis.app.ui.SplashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃兜底：把堆栈写到本地文件，并在未发生连环崩溃时自动把用户送回启动页。
 * 目的很朴素——真机上万一崩了，不是直接黑屏退出，而是能重新进来自证可用，
 * 同时留下一份可诊断的日志（「运行状态」页可查看）。
 */
object CrashGuard {

    private const val PREFS = "shine_cache"
    private const val KEY_LAST_CRASH = "last_crash_at"
    private const val KEY_LAST_TRACE = "last_crash_trace"
    private const val FILE = "last_crash.txt"
    private const val LOOP_WINDOW_MS = 60_000L

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { sw ->
                    PrintWriter(sw).use { throwable.printStackTrace(it) }
                }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                val body = "时间：$stamp\n线程：${thread.name}\n\n$trace"
                File(app.filesDir, FILE).writeText(body)

                val now = System.currentTimeMillis()
                val last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_LAST_CRASH, 0L)
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_CRASH, now)
                    .putString(KEY_LAST_TRACE, body.take(600))
                    .apply()

                // 60 秒内二次崩溃说明是必现问题，别再把用户拉进循环
                if (now - last > LOOP_WINDOW_MS) {
                    val intent = Intent(app, SplashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    app.startActivity(intent)
                }
            } catch (ignored: Exception) {
                // 兜底逻辑本身失败就交给系统默认处理
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    /** 最近一次崩溃摘要，没有则返回 null */
    fun lastCrash(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_TRACE, null)

    fun crashCount(ctx: Context): Int = if (File(ctx.filesDir, FILE).exists()) 1 else 0
}
