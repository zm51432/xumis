package com.shine.xumis.app.model

/** 分类项（后端 /api/m/categories 返回 code + label） */
data class CategoryItem(
    val code: String? = null,
    val label: String? = null
)
