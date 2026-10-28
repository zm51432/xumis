package com.shine.xumis.app.model

/** 健康检查响应（/api/m/ping） */
data class PingResponse(
    val ok: Boolean? = null,
    val service: String? = null,
    val ts: Long? = null
)
