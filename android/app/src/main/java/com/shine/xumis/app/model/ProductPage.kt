package com.shine.xumis.app.model

/** 分页商品列表（/api/m/products、/api/m/search） */
data class ProductPage(
    val content: List<MobileProduct>? = null,
    val page: Int? = null,
    val size: Int? = null,
    val totalElements: Long? = null,
    val totalPages: Int? = null
)
