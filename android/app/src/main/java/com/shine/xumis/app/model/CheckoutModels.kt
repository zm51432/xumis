package com.shine.xumis.app.model

/** 可选支付方式 */
data class PayMethod(
    val code: String? = null,
    val label: String? = null
)

/** 结算页里预填的收货资料（已登录时后端带出） */
data class CheckoutUser(
    val loggedIn: Boolean = false,
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val country: String? = null,
    val address: String? = null
)

/** 结算页数据：购物车 + 收货资料 + 支付方式 */
data class CheckoutInfo(
    val ok: Boolean = false,
    val items: List<CartItem> = emptyList(),
    val count: Int = 0,
    val total: Double = 0.0,
    val currency: String? = "USD",
    val user: CheckoutUser? = null,
    val methods: List<PayMethod> = emptyList(),
    val subtotal: Double = 0.0,
    val shippingFee: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discount: Double = 0.0
)

/** 下单结果 */
data class PlaceResult(
    val ok: Boolean = false,
    val orderNo: String? = null,
    val total: Double? = null,
    val currency: String? = null,
    val paymentMethod: String? = null,
    val error: String? = null
)
