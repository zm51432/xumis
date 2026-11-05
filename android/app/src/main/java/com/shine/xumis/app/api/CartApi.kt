package com.shine.xumis.app.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 购物车原生加购（对外统一入口）。
 *
 * 1.9.0 起购物车/结算全部走 App 原生接口（/api/m/cart 等），与会话共享一个 Cookie 存储，
 * 不再依赖 WebView Cookie 或抓取 CSRF 令牌。这里保留「变更广播」：加购/改数量/删除/下单后
 * 通知底栏角标刷新、购物车页重载。
 */
object CartApi {

    private val _changes = MutableSharedFlow<Int>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val changes = _changes.asSharedFlow()

    fun publish(count: Int) {
        _changes.tryEmit(count)
    }

    /** 加入购物车（可带规格 skuId），返回加购后购物车总件数 */
    suspend fun add(productId: Long, qty: Int = 1, skuId: Long? = null): Int =
        CommerceApi.add(productId, qty, skuId)

    /** 刷新当前购物车件数（购物车页删除/改数量后用它同步角标） */
    suspend fun refreshCount(): Int = CommerceApi.cart().count
}
