package com.shine.xumis.app.model

/**
 * 健康检查响应（/api/m/ping）
 * 后端返回：{ ok, service, ts, dbOk, dbMs, ms }
 */
data class PingResponse(
    val ok: Boolean? = null,
    val service: String? = null,
    val ts: Long? = null,
    val dbOk: Boolean? = null,
    val dbMs: Long? = null,
    val ms: Long? = null
)
