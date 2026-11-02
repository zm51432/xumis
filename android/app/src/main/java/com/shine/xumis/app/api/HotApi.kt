package com.shine.xumis.app.api

import com.google.gson.GsonBuilder
import com.shine.xumis.app.util.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 热销榜（按销量排序）。
 *
 * 后端商品接口没有销量字段，也不支持按销量排序；但站点商品页会展示「已售 N 件」。
 * 服务端定时（每 10 分钟）扫描订单汇总出热销榜，落盘到 /download/hot.json，
 * App 首页拉它来：① 把商品按销量优先排序；② 在卡片上展示「已售 N 件」。
 *
 * 这是纯静态文件，失败不影响主流程（降级为默认顺序）。
 */
object HotApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder().create()

    data class HotItem(
        val id: Long,
        val sales: Int,
        val name: String?,
        val category: String?
    )

    data class HotResult(
        val total: Int,
        val updatedAt: String?,
        val items: List<HotItem>
    )

    /** 返回 商品ID 到 销量的映射；查不到返回空 map */
    fun loadSalesMap(): Map<Long, Int> {
        return runCatching {
            val url = ServerConfig.siteBase() + "download/hot.json"
            val json = client.newCall(Request.Builder().url(url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyMap()
                    resp.body?.string() ?: return@runCatching emptyMap()
                }
            val root = gson.fromJson(json, HotResult::class.java)
            root.items.associate { it.id to it.sales }
        }.getOrDefault(emptyMap())
    }
}
