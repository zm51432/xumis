package com.shine.xumis.app.api

import com.shine.xumis.app.model.CategoryItem
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.model.PingResponse
import com.shine.xumis.app.model.ProductPage
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * shine 星心移动端 API（后端 /api/m 前缀由 Retrofit baseUrl 提供）。
 */
interface ShineApi {

    /** 健康检查 */
    @GET("ping")
    suspend fun ping(): PingResponse

    /** 商品列表（分页） */
    @GET("products")
    suspend fun products(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30
    ): ProductPage

    /** 单个商品详情 */
    @GET("products/{id}")
    suspend fun product(@Path("id") id: Long): MobileProduct

    /** 分类列表 */
    @GET("categories")
    suspend fun categories(): List<CategoryItem>

    /** 搜索 / 分类 / 排序 / 分页 */
    @GET("search")
    suspend fun search(
        @Query("q") q: String? = null,
        @Query("cat") cat: String? = null,
        @Query("sort") sort: String = "default",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30
    ): ProductPage
}
