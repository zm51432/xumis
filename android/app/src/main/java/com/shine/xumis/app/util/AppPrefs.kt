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
    private const val KEY_PERM_PROMPTED = "perm_prompted"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_SKIPPED_VERSION = "skipped_version_code"

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

    /**
     * 是否已经引导过权限。只在首次启动弹一次说明，
     * 之后随时可以从「设置 → 权限管理」进入，避免每次启动都打扰用户。
     */
    var permissionPrompted: Boolean
        get() = store()?.getBoolean(KEY_PERM_PROMPTED, false) ?: false
        set(value) {
            store()?.edit()?.putBoolean(KEY_PERM_PROMPTED, value)?.apply()
        }

    /**
     * 上次成功检查更新的时间戳。启动时静默检查有频率限制，
     * 避免每次切回前台都打一次接口。
     */
    var lastUpdateCheck: Long
        get() = store()?.getLong(KEY_LAST_UPDATE_CHECK, 0L) ?: 0L
        set(value) {
            store()?.edit()?.putLong(KEY_LAST_UPDATE_CHECK, value)?.apply()
        }

    /**
     * 用户点了「稍后再说」的版本号。同一个版本不再反复弹窗，
     * 但服务端标记为强制更新时不受此限制。
     */
    var skippedVersionCode: Int
        get() = store()?.getInt(KEY_SKIPPED_VERSION, 0) ?: 0
        set(value) {
            store()?.edit()?.putInt(KEY_SKIPPED_VERSION, value)?.apply()
        }
}
