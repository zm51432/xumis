package com.shine.xumis.app.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本机记录：来图定制 / 在线咨询提交后落一份到本地，
 * 让「提交完就消失」的操作变成可回看、可追踪的闭环。
 *
 * 只存本机，不上传；服务端仍是权威数据源（后台可查全部提交）。
 */
data class LocalRecord(
    val id: Long,
    /** custom = 来图定制，inquiry = 在线咨询 */
    val type: String,
    val title: String,
    val detail: String,
    val email: String,
    val createdAt: Long,
    val status: String
)

object LocalRecords {

    private const val PREFS = "shine_local_records"
    private const val KEY = "records"
    private const val MAX = 60

    const val TYPE_CUSTOM = "custom"
    const val TYPE_INQUIRY = "inquiry"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(ctx: Context, type: String, title: String, detail: String, email: String) {
        val list = all(ctx).toMutableList()
        list.add(
            0,
            LocalRecord(
                id = System.currentTimeMillis(),
                type = type,
                title = title,
                detail = detail,
                email = email,
                createdAt = System.currentTimeMillis(),
                status = "已提交"
            )
        )
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun all(ctx: Context): List<LocalRecord> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LocalRecord(
                    id = o.optLong("id"),
                    type = o.optString("type"),
                    title = o.optString("title"),
                    detail = o.optString("detail"),
                    email = o.optString("email"),
                    createdAt = o.optLong("createdAt"),
                    status = o.optString("status").ifBlank { "已提交" }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(ctx: Context, list: List<LocalRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("type", r.type)
                put("title", r.title)
                put("detail", r.detail)
                put("email", r.email)
                put("createdAt", r.createdAt)
                put("status", r.status)
            })
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
