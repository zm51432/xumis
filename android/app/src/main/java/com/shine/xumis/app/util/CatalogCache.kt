package com.shine.xumis.app.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shine.xumis.app.model.MobileProduct
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 商品列表本地快照。
 *
 * 每次成功拉到商品列表就落盘一份；下次打开 App 若网络不可用，
 * 直接把这份快照渲染出来并标注「离线数据 · 缓存于 xx」，而不是空列表 + 报错。
 */
object CatalogCache {

    private const val FILE = "catalog_snapshot.json"
    private const val PREFS = "shine_cache"
    private const val KEY_SAVED_AT = "catalog_saved_at"

    private val gson = Gson()
    private val listType = object : TypeToken<List<MobileProduct>>() {}.type

    data class Snapshot(val items: List<MobileProduct>, val savedAt: Long)

    fun save(ctx: Context, items: List<MobileProduct>) {
        if (items.isEmpty()) return
        try {
            File(ctx.filesDir, FILE).writeText(gson.toJson(items))
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_SAVED_AT, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            // 缓存失败不影响在线流程
        }
    }

    fun load(ctx: Context): Snapshot? {
        return try {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) return null
            val items: List<MobileProduct> = gson.fromJson(f.readText(), listType) ?: return null
            if (items.isEmpty()) return null
            val at = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_SAVED_AT, f.lastModified())
            Snapshot(items, at)
        } catch (e: Exception) {
            null
        }
    }

    fun count(ctx: Context): Int = load(ctx)?.items?.size ?: 0

    fun savedAtText(ctx: Context): String {
        val at = load(ctx)?.savedAt ?: return "无缓存"
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return fmt.format(Date(at))
    }
}
