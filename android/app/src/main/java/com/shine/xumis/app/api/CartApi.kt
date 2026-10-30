package com.shine.xumis.app.api

import android.webkit.CookieManager
import com.shine.xumis.app.util.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 购物车原生加购。
 *
 * 关键点：站点登录态存在 WebView 自己的 CookieManager 里，和 OkHttp 不是同一份。
 * 原生加购必须**带上 WebView 当前在该域名的 Cookie**，才能和站内购物车是同一个购物车，
 * 否则会出现「首页加了 1 件，点进购物车却看不到」的割裂。
 *
 * 流程：读 WebView Cookie → 取商品页的 CSRF 令牌 → POST /cart/add-json。
 */
object CartApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 购物车变化的广播：加购成功后通知底栏角标刷新、购物车页自动重载 */
    private val _changes = MutableSharedFlow<Int>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val changes = _changes.asSharedFlow()

    fun publish(count: Int) {
        _changes.tryEmit(count)
    }

    /** 读取 WebView 在该域名下的会话 Cookie（可能为空，匿名也能加购） */
    private fun webCookies(path: String): String? =
        runCatching { CookieManager.getInstance().getCookie(ServerConfig.siteBase() + path) }
            .getOrNull()

    /**
     * 加入购物车。返回加购后购物车总件数；失败抛异常由调用方提示。
     */
    suspend fun add(productId: Long, qty: Int = 1): Int = withContext(Dispatchers.IO) {
        val base = ServerConfig.siteBase()
        val cookie = webCookies("product/$productId")

        // ① 抓商品页里的 CSRF 令牌（与当前会话绑定）
        val get = Request.Builder()
            .url(base + "product/$productId")
            .header("Cookie", cookie ?: "")
            .build()
        val html = client.newCall(get).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("页面为空")
        }
        val csrf = Regex("name=\"_csrf\" content=\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("缺少安全令牌，请稍后重试")

        // ② 调加购接口
        val post = Request.Builder()
            .url(base + "cart/add-json")
            .header("Cookie", cookie ?: "")
            .header("X-CSRF-Token", csrf)
            .header("Accept", "application/json")
            .post(
                FormBody.Builder()
                    .add("productId", productId.toString())
                    .add("quantity", qty.toString())
                    .build()
            )
            .build()
        val json = client.newCall(post).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("响应为空")
        }
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok", false)) {
            throw IllegalStateException(obj.optString("message", "加购失败"))
        }
        obj.optInt("count", 0)
    }
}
