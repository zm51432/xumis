package com.shine.xumis.app.util

/** 纯函数：地址归一化与拼接，无 Android 依赖，便于单元测试。 */
object UrlUtils {

    /** 线上服务器（APK 出厂默认） */
    const val PROD_URL = "https://shine.xumois.cn"

    /** 模拟器访问宿主机后端的调试地址 */
    const val EMULATOR_URL = "http://10.0.2.2:8080"

    fun join(base: String, path: String): String {
        val b = if (base.endsWith("/")) base else "$base/"
        val p = if (path.startsWith("/")) path.removePrefix("/") else path
        return b + p
    }

    /**
     * 归一化服务器基址：
     * - 空 → 线上默认地址
     * - 缺协议：xumois.cn 域名补 https，其它（IP/局域网）补 http
     * - 统一补结尾斜杠，保证 <base>api/m/ 之类的拼接不会缺斜杠
     */
    fun normalizeBase(input: String): String {
        var v = input.trim()
        // 空值回落线上默认地址；注意 PROD_URL 常量本身不带结尾斜杠，这里必须走下面的补斜杠逻辑
        if (v.isEmpty()) v = PROD_URL
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = if (v.contains("xumois.cn")) "https://$v" else "http://$v"
        }
        if (!v.endsWith("/")) v = "$v/"
        return v
    }
}
