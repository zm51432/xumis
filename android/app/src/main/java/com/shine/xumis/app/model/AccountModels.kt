package com.shine.xumis.app.model

/** 发验证码 / 提交表单的通用结果 */
data class CodeResult(
    val ok: Boolean = false,
    val error: String? = null,
    val email: String? = null,
    val name: String? = null
)

/** 账单/订单详情里的单行商品 */
data class OrderLine(
    val productId: Long? = null,
    val name: String? = null,
    val nameEn: String? = null,
    val price: Double? = null,
    val quantity: Int? = null,
    val imageUrl: String? = null,
    val skuLabel: String? = null
)

/** 账单/订单详情（对应后端 /api/m/order/{orderNo}） */
data class OrderDetail(
    val ok: Boolean = false,
    val error: String? = null,
    val orderNo: String? = null,
    val status: String? = null,
    val paymentMethod: String? = null,
    val totalAmount: Double? = null,
    val currency: String? = "USD",
    val createdAt: String? = null,
    val customerName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val country: String? = null,
    val address: String? = null,
    val note: String? = null,
    val items: List<OrderLine> = emptyList(),
    val subtotalAmount: Double? = null,
    val discountAmount: Double? = null,
    val shippingFee: Double? = null,
    val taxAmount: Double? = null,
    val couponCode: String? = null,
    val trackingNo: String? = null,
    val carrier: String? = null,
    val trackingUrl: String? = null,
    val shippedAt: String? = null,
    val trackingEvents: List<TrackingEvent> = emptyList()
)
