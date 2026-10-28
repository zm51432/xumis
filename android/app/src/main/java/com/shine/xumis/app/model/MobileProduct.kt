package com.shine.xumis.app.model

import com.google.gson.annotations.SerializedName
import com.shine.xumis.app.util.ServerConfig

/**
 * 移动端商品视图（对应后端 MobileProductDto）。
 * 后端字段：id,name,nameEn,displayName,category,price,imageUrl,
 *          customizable,material,stock,featured,description,inStock
 */
data class MobileProduct(
    val id: Long? = null,
    val name: String? = null,
    @SerializedName("nameEn") val nameEn: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    val category: String? = null,
    val price: Double? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    val customizable: Boolean? = null,
    val material: String? = null,
    val stock: Int? = null,
    val featured: Boolean? = null,
    val description: String? = null,
    @SerializedName("inStock") val inStock: Boolean? = null
) {
    /** 展示名称：优先 displayName，其次中文名 */
    fun title(): String = displayName ?: name ?: "商品"

    /** 价格文本：$xx.xx */
    fun priceText(): String = if (price != null) "$" + String.format("%.2f", price) else ""

    /** 完整图片地址（拼接站点基址） */
    fun fullImageUrl(): String? {
        if (imageUrl.isNullOrBlank()) return null
        return if (imageUrl!!.startsWith("/")) ServerConfig.siteBase() + imageUrl!!.removePrefix("/") else imageUrl
    }

    /** 库存状态文本 */
    fun stockText(): String = when {
        inStock == false -> "暂时缺货"
        stock != null && stock!! > 0 -> "现货 $stock 件"
        else -> "现货"
    }
}
