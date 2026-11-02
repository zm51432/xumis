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
    @SerializedName("inStock") val inStock: Boolean? = null,
    /** 累计销量（用于首页「按销量排序」与「已售」展示；后端接口不返回时由热销榜补齐） */
    @SerializedName("sales") val sales: Int? = null
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

    /** 已售文本：热销榜或商品字段里有销量时显示「已售 N 件」 */
    fun soldText(): String? {
        val s = sales ?: return null
        if (s <= 0) return null
        return "已售 $s 件"
    }
}
