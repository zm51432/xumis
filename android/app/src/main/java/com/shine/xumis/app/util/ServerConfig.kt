package com.shine.xumis.app.util

import android.content.Context

/**
 * 服务端地址：固定指向线上服务，**不在任何界面展示、也不允许用户修改**。
 *
 * 1.2.x 及更早版本把地址做成「服务器设置」页里的输入框，用户装上就先看到一串 URL，
 * 既不专业，也容易被误改导致连不上。现在地址只存在于代码内部，App 后台直连；
 * 升级时顺手清掉旧版本留在 SharedPreferences 里的自定义地址。
 *
 * 调试需求（本地后端）请改 UrlUtils.PROD_URL 后重新打包，不要在运行时暴露入口。
 */
object ServerConfig {

    /** 线上服务地址（内部常量，界面不渲染） */
    private const val HOST = UrlUtils.PROD_URL

    private const val LEGACY_PREFS = "shine_prefs"
    private const val LEGACY_KEY_URL = "server_url"

    /** 站点基址（带结尾斜杠）：WebView 拼 /cart、/product/1、/download/ 用 */
    fun siteBase(): String = UrlUtils.normalizeBase(HOST)

    /** 移动端 API 基址：<host>/api/m/ */
    fun apiBase(): String = UrlUtils.join(HOST, "api/m/")

    /** 首次启动清理旧版遗留的地址配置，避免旧值被误读 */
    fun purgeLegacy(ctx: Context) {
        val p = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (p.contains(LEGACY_KEY_URL)) {
            p.edit().remove(LEGACY_KEY_URL).apply()
        }
    }
}
