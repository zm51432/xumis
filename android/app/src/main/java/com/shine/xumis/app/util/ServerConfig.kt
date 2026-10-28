package com.shine.xumis.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 服务器地址配置（SharedPreferences 持久化）。
 * - 模拟器访问宿主机后端用 http://10.0.2.2:8080
 * - 真机访问局域网/公网部署服务器，请改成实际可访问地址
 */
object ServerConfig {
    private const val PREFS = "shine_prefs"
    private const val KEY_URL = "server_url"
    const val DEFAULT_URL = "http://10.0.2.2:8080"

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() {
            val u = prefs.getString(KEY_URL, null) ?: DEFAULT_URL
            return normalize(u)
        }
        set(value) = prefs.edit().putString(KEY_URL, normalize(value)).apply()

    private fun normalize(input: String): String {
        var v = input.trim()
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
        if (!v.endsWith("/")) v = "$v/"
        return v
    }

    /** 移动端 API 基址：<base>/api/m/ */
    fun apiBase(): String = baseUrl + "api/m/"

    /** 站点基址（WebView 用） */
    fun siteBase(): String = baseUrl
}
