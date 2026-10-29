package com.shine.xumis.app.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近一次接口健康检查的结果，持久化保存。
 * 「运行状态」页读它来回答一个问题：这套 App ↔ 服务端 到底靠不靠得住。
 */
object HealthLog {

    private const val PREFS = "shine_health"
    private const val K_PASS = "pass"
    private const val K_FAIL = "fail"
    private const val K_AVG = "avg"
    private const val K_MAX = "max"
    private const val K_AT = "at"
    private const val K_LAUNCH = "first_launch"

    data class Record(
        val pass: Int,
        val fail: Int,
        val avgMs: Long,
        val maxMs: Long,
        val at: Long
    ) {
        val total: Int get() = pass + fail
        val rate: Int get() = if (total == 0) 0 else (pass * 100 / total)
        val timeText: String
            get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(at))
    }

    fun save(ctx: Context, pass: Int, fail: Int, avgMs: Long, maxMs: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(K_PASS, pass)
            .putInt(K_FAIL, fail)
            .putLong(K_AVG, avgMs)
            .putLong(K_MAX, maxMs)
            .putLong(K_AT, System.currentTimeMillis())
            .apply()
    }

    fun last(ctx: Context): Record? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(K_AT)) return null
        return Record(
            p.getInt(K_PASS, 0), p.getInt(K_FAIL, 0),
            p.getLong(K_AVG, 0), p.getLong(K_MAX, 0), p.getLong(K_AT, 0)
        )
    }

    /** 首次安装时间（用于展示「已稳定使用 N 天」） */
    fun firstLaunch(ctx: Context): Long {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = p.getLong(K_LAUNCH, 0L)
        if (v == 0L) {
            p.edit().putLong(K_LAUNCH, System.currentTimeMillis()).apply()
            return System.currentTimeMillis()
        }
        return v
    }

    fun daysInUse(ctx: Context): Int {
        val ms = System.currentTimeMillis() - firstLaunch(ctx)
        return (ms / 86_400_000L).toInt() + 1
    }
}
