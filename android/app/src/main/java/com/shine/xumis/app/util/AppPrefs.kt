package com.shine.xumis.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * App 本地偏好设置（只存展示类选项，不含任何服务端地址）。
 *
 * 目前只有「网页字号」：站点页面在 WebView 里默认按 CSS 像素排版，
 * 长辈用户常觉得偏小，这里提供 标准 / 大 / 特大 三档，通过 WebSettings.textZoom 生效。
 */
object AppPrefs {

    private const val PREFS = "shine_settings"
    private const val KEY_TEXT_ZOOM = "web_text_zoom"

    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun store(): SharedPreferences? = prefs

    /** 0 标准 100% / 1 大 115% / 2 特大 130% */
    var textZoomIndex: Int
        get() = store()?.getInt(KEY_TEXT_ZOOM, 0) ?: 0
        set(value) {
            store()?.edit()?.putInt(KEY_TEXT_ZOOM, value.coerceIn(0, 2))?.apply()
        }

    fun textZoomPercent(): Int = when (textZoomIndex) {
        1 -> 115
        2 -> 130
        else -> 100
    }

    fun textZoomLabel(): String = when (textZoomIndex) {
        1 -> "大"
        2 -> "特大"
        else -> "标准"
    }

    val textZoomOptions = arrayOf("标准", "大（115%）", "特大（130%）")
}
