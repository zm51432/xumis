package com.shine.xumis.app.api

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 会话 Cookie 仓库：把服务端下发的 Cookie（JSESSIONID）持久化到本地，
 * 这样 App 重启后仍然是登录态、购物车里也还在。
 *
 * 注：App 已完全移除 WebView，这里只做本地持久化，不再与网页 Cookie 互通。
 */
class SessionCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences("shine_session", Context.MODE_PRIVATE)
    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val editor = prefs.edit()
            cookies.forEach { c -> editor.putString(key(c.domain, c.name), c.value) }
            editor.apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val out = ArrayList<Cookie>()
        synchronized(lock) {
            prefs.all.forEach { (k, v) ->
                val value = v as? String ?: return@forEach
                val parts = k.split("|")
                if (parts.size != 2) return@forEach
                val domain = parts[0]
                if (!matches(host, domain)) return@forEach
                runCatching {
                    out.add(
                        Cookie.Builder()
                            .domain(domain)
                            .path("/")
                            .name(parts[1])
                            .value(value)
                            .build()
                    )
                }
            }
        }
        return out
    }

    /** 清空（退出登录时用） */
    fun clear() {
        synchronized(lock) {
            prefs.edit().clear().apply()
        }
    }

    private fun key(domain: String, name: String) = "$domain|$name"

    private fun matches(host: String, domain: String): Boolean {
        val d = domain.trimStart('.')
        return host == d || host.endsWith(".$d")
    }
}
