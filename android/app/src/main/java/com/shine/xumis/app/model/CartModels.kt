package com.shine.xumis.app.model

/** 购物车行（对应后端 /api/m/cart 的 items 元素） */
data class CartItem(
    val productId: Long? = null,
    val name: String? = null,
    val nameEn: String? = null,
    val price: Double? = null,
    val quantity: Int? = null,
    val stock: Int? = null,
    val imageUrl: String? = null,
    val lineTotal: Double? = null,
    val skuId: Long? = null,
    val skuLabel: String? = null
)

/** 购物车整体（加购/改量/删除接口也返回同一结构，便于直接刷新界面） */
data class CartResponse(
    val ok: Boolean = true,
    val message: String? = null,
    val items: List<CartItem> = emptyList(),
    val count: Int = 0,
    val total: Double = 0.0,
    val currency: String? = "USD"
)

/** 账号页里的订单摘要 */
data class OrderBrief(
    val orderNo: String? = null,
    val status: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = null,
    val createdAt: String? = null,
    val itemCount: Int? = null
)

/** 当前登录状态与账号信息 */
data class MeResponse(
    val loggedIn: Boolean = false,
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val country: String? = null,
    val address: String? = null,
    val emailVerified: Boolean = false,
    val orders: List<OrderBrief> = emptyList()
)

/** 登录结果 */
data class LoginResponse(
    val ok: Boolean = false,
    val email: String? = null,
    val name: String? = null,
    val error: String? = null,
    val remainingSeconds: Long? = null
)

data class OkResponse(val ok: Boolean = true)
