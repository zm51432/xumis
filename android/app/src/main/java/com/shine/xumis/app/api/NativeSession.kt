package com.shine.xumis.app.api

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 原生交易会话：App 内购物车 / 下单 / 我的账户共用的一个 OkHttp 客户端。
 *
 * 后端把购物车与登录态放在 HttpSession（JSESSIONID Cookie）里。这里用
 * android.webkit.CookieManager 作为 Cookie 存储——它是应用级共享、自动落盘的，
 * 既保证「原生加购 → 原生结算 → 原生我的」是同一个会话，也兼容 App 里残留的
 * 少数 WebView 页面（更多服务 / 离线商品册）共用同一份登录态。
 */
object NativeSession {

    val client: OkHttpClient by lazy {
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val cm = CookieManager.getInstance()
                for (c in cookies) {
                    runCatching { cm.setCookie(url.toString(), c.toString()) }
                }
                runCatching { cm.flush() }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val header = runCatching {
                    CookieManager.getInstance().getCookie(url.toString())
                }.getOrNull() ?: return emptyList()
                if (header.isBlank()) return emptyList()
                // CookieManager 返回的是「Cookie 请求头」("a=1; b=2")，逐段解析成 Cookie
                return header.split(";").mapNotNull { part ->
                    val p = part.trim()
                    if (p.isEmpty()) null
                    else runCatching { Cookie.parse(url, p) }.getOrNull()
                }
            }
        }
        OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
