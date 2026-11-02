package com.shine.xumis.app.util

import android.content.Context

/**
 * 默认支付方式偏好：用户可在「我的 → 支付方式」里选一个默认渠道，
 * 结算页据此预选，避免每次都重新点。
 * 只存本地偏好（网站暂无每账户支付方式模型）。
 */
object PaymentPrefs {
    private const val PREFS = "shine_payment"
    private const val KEY_METHOD = "default_method"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveDefault(ctx: Context, method: String) {
        prefs(ctx).edit().putString(KEY_METHOD, method).apply()
    }

    fun defaultMethod(ctx: Context): String? =
        prefs(ctx).getString(KEY_METHOD, null)
}
