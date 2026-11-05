package com.shine.xumis.app.model

/** 阶段3：商品规格（SKU） */
data class SkuOption(
    val skuId: Long? = null,
    val skuCode: String? = null,
    val label: String? = null,
    val labelEn: String? = null,
    val priceDelta: Double? = null,
    val stock: Int? = null
)

/** 阶段3：优惠码校验结果（含运费/税费重算） */
data class CouponQuote(
    val ok: Boolean = false,
    val message: String? = null,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val shippingFee: Double = 0.0,
    val taxAmount: Double = 0.0,
    val total: Double = 0.0,
    val freeShipping: Boolean = false
)

/** 阶段3：物流轨迹节点 */
data class TrackingEvent(
    val eventTime: String? = null,
    val location: String? = null,
    val description: String? = null
)
