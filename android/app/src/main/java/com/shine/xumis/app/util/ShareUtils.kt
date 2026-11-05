package com.shine.xumis.app.util

import android.content.Context
import android.content.Intent

/** 纯系统分享（不打开任何网页）：拉起系统分享面板发送文本/链接 */
object ShareUtils {
    fun shareIntent(title: String, text: String, url: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, if (url.isBlank()) text else "$text\n$url")
        }
        return Intent.createChooser(send, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun share(ctx: Context, title: String, text: String, url: String = "") {
        ctx.startActivity(shareIntent(title, text, url))
    }
}
