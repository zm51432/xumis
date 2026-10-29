package com.shine.xumis.app.api

import com.google.gson.GsonBuilder
import com.shine.xumis.app.util.ServerConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端。服务端地址固定（内置常量），首次调用时构建后复用。
 * 走直连（不经过系统代理），与后端原生 HTTP 通信。
 */
object ApiClient {
    private var retrofit: Retrofit? = null
    private var api: ShineApi? = null

    @Synchronized
    fun api(): ShineApi {
        val base = ServerConfig.apiBase()
        if (retrofit == null || retrofit!!.baseUrl().toString() != base) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
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
