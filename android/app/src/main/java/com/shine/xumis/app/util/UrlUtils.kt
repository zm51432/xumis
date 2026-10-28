package com.shine.xumis.app.util

/** 纯函数：拼接 base 与 path，自动处理斜杠。无 Android 依赖，便于单元测试。 */
object UrlUtils {
    fun join(base: String, path: String): String {
        val b = if (base.endsWith("/")) base else "$base/"
        val p = if (path.startsWith("/")) path.removePrefix("/") else path
        return b + p
    }
}
