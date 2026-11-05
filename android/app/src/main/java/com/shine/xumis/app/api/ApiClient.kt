package com.shine.xumis.app.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.shine.xumis.app.util.ServerConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端。服务端地址固定（内置常量），首次调用时构建后复用。
 * 走直连（不经过系统代理），与后端原生 HTTP 通信。
 *
 * 带持久化 CookieJar：购物车与登录态都挂在 JSESSIONID 上，
 * App 已移除 WebView，Cookie 只在原生网络层本地持久化。
 */
object ApiClient {
    private var retrofit: Retrofit? = null
    private var api: ShineApi? = null
    private var jar: SessionCookieJar? = null

    /** 在 Application.onCreate 里调用一次 */
    fun init(context: Context) {
        if (jar == null) {
            jar = SessionCookieJar(context.applicationContext)
        }
    }

    /** 退出登录：清掉本地会话 */
    fun clearSession() {
        jar?.clear()
        reset()
    }

    @Synchronized
    fun api(): ShineApi {
        val base = ServerConfig.apiBase()
        if (retrofit == null || retrofit!!.baseUrl().toString() != base) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
            jar?.let { builder.cookieJar(it) }
            val client = builder.build()
            val gson = GsonBuilder().setLenient().create()
            retrofit = Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            api = retrofit!!.create(ShineApi::class.java)
        }
        return api!!
    }

    /** 切换服务器后强制重建（下次调用 api() 时会用新地址） */
    fun reset() {
        retrofit = null
        api = null
    }
}
