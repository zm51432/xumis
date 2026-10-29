package com.shine.xumis.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 服务器地址配置（SharedPreferences 持久化）。
 *
 * 默认值即为线上服务器 https://shine.xumois.cn，APK 安装后无需任何设置即可连上服务。
 * - 线上：https://shine.xumois.cn（Nginx 443 → 后端 8080）
 * - 模拟器调试：http://10.0.2.2:8080（需在设置页手动切换）
 * - 局域网真机调试：http://<电脑内网IP>:8080（需在设置页手动切换）
 */
object ServerConfig {
    private const val PREFS = "shine_prefs"
    private const val KEY_URL = "server_url"
    private const val KEY_MIGRATED_V2 = "url_migrated_v2"

    /** 线上服务器：APK 出厂默认连接地址 */
    const val PROD_URL = UrlUtils.PROD_URL

    /** 模拟器访问宿主机后端的调试地址 */
    const val EMULATOR_URL = UrlUtils.EMULATOR_URL

    /** 默认地址（= 线上服务器） */
    const val DEFAULT_URL = PROD_URL

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrateLegacyDefault()
    }

    var baseUrl: String
        get() {
            val u = prefs.getString(KEY_URL, null) ?: DEFAULT_URL
            return normalize(u)
        }
        set(value) = prefs.edit().putString(KEY_URL, normalize(value)).apply()

    /** 是否仍在使用线上默认地址（用户未自定义） */
    fun isUsingDefault(): Boolean = baseUrl == normalize(PROD_URL)

    /**
     * 旧版本默认值是模拟器地址 10.0.2.2:8080，已安装用户升级后会带着这个旧值，
     * 真机上永远连不上，所以升级时把它清掉、回落到新的线上默认值。
     * 只清理「恰好等于旧默认值」的情况，用户自己改过的地址不受影响。
     */
    private fun migrateLegacyDefault() {
        if (prefs.getBoolean(KEY_MIGRATED_V2, false)) return
        val saved = prefs.getString(KEY_URL, null)
        val editor = prefs.edit().putBoolean(KEY_MIGRATED_V2, true)
        if (saved != null && normalize(saved) == normalize(EMULATOR_URL)) {
            editor.remove(KEY_URL)
        }
        editor.apply()
    }

    private fun normalize(input: String): String = UrlUtils.normalizeBase(input)

    /** 移动端 API 基址：<base>/api/m/ */
    fun apiBase(): String = baseUrl + "api/m/"

    /** 站点基址（WebView 用） */
    fun siteBase(): String = baseUrl
}
