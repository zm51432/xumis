package com.shine.xumis.app.api

import com.shine.xumis.app.model.CartResponse
import com.shine.xumis.app.model.CategoryItem
import com.shine.xumis.app.model.CheckoutInfo
import com.shine.xumis.app.model.CodeResult
import com.shine.xumis.app.model.CouponQuote
import com.shine.xumis.app.model.SkuOption
import com.shine.xumis.app.model.LoginResponse
import com.shine.xumis.app.model.MeResponse
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.model.OkResponse
import com.shine.xumis.app.model.OrderDetail
import com.shine.xumis.app.model.PingResponse
import com.shine.xumis.app.model.PlaceResult
import com.shine.xumis.app.model.ProductPage
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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

    /** 阶段3：商品规格列表 */
    @GET("products/{id}/skus")
    suspend fun productSkus(@Path("id") id: Long): List<SkuOption>

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

    // ==================== 购物车（原生页面） ====================

    @GET("cart")
    suspend fun cart(): CartResponse

    @FormUrlEncoded
    @POST("cart/add")
    suspend fun cartAdd(
        @Field("productId") productId: Long,
        @Field("quantity") quantity: Int = 1,
        @Field("skuId") skuId: Long? = null
    ): CartResponse

    @FormUrlEncoded
    @POST("cart/set")
    suspend fun cartSet(
        @Field("productId") productId: Long,
        @Field("quantity") quantity: Int
    ): CartResponse

    @FormUrlEncoded
    @POST("cart/remove")
    suspend fun cartRemove(@Field("productId") productId: Long): CartResponse

    // ==================== 结算 / 下单 ====================

    @GET("checkout")
    suspend fun checkout(): CheckoutInfo

    /** 阶段3：结算页优惠码校验 */
    @FormUrlEncoded
    @POST("coupon-check")
    suspend fun couponCheck(@Field("code") code: String): CouponQuote

    /** 提交订单；参数不合规会返回 400，用 Response 包一层读取错误码 */
    @FormUrlEncoded
    @POST("order/place")
    suspend fun placeOrder(
        @Field("customerName") customerName: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("country") country: String,
        @Field("address") address: String,
        @Field("note") note: String,
        @Field("currency") currency: String = "USD",
        @Field("paymentMethod") paymentMethod: String,
        @Field("couponCode") couponCode: String? = null
    ): Response<PlaceResult>

    // ==================== 账号（原生页面） ====================

    @GET("me")
    suspend fun me(): MeResponse

    /** 登录失败返回 401，用 Response 包一层以便读取状态码 */
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @POST("logout")
    suspend fun logout(): OkResponse

    // ==================== 注册 / 找回密码（App 内完成） ====================

    /** 注册前发送邮箱验证码 */
    @FormUrlEncoded
    @POST("register/send-code")
    suspend fun registerSendCode(@Field("email") email: String): CodeResult

    /** 提交注册（验证码通过才建号，成功后直接登录） */
    @FormUrlEncoded
    @POST("register")
    suspend fun register(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("name") name: String,
        @Field("code") code: String
    ): Response<CodeResult>

    /** 找回密码：发送邮箱验证码 */
    @FormUrlEncoded
    @POST("password/send-code")
    suspend fun passwordSendCode(@Field("email") email: String): CodeResult

    /** 找回密码：验证码 + 新密码直接改密 */
    @FormUrlEncoded
    @POST("password/reset")
    suspend fun passwordReset(
        @Field("email") email: String,
        @Field("code") code: String,
        @Field("newPassword") newPassword: String
    ): Response<CodeResult>

    // ==================== 账单 / 订单详情 ====================

    @GET("order/{orderNo}")
    suspend fun orderDetail(@Path("orderNo") orderNo: String): Response<OrderDetail>
}
