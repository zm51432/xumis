package com.shine.xumis.app.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查与安装包下载。
 *
 * 服务端是站点自建的推送服务（发布管理台），App 每次启动或手动「检查更新」时
 * 带上当前 versionCode 询问一次，服务端返回线上版本；只有高于本机才提示升级。
 *
 * 安全约束：
 *  - 只认 HTTPS 地址，服务端返回的 url 也必须是 https 且与站点同域；
 *  - 下载完成后按服务端给出的 SHA-256 校验，不一致直接删除不安装；
 *  - 安装走系统安装器（FileProvider + ACTION_VIEW），不静默安装。
 */
object UpdateChecker {

    /** 服务端返回的一个版本 */
    data class Release(
        @SerializedName("versionCode") val versionCode: Int = 0,
        @SerializedName("versionName") val versionName: String = "",
        @SerializedName("size") val size: Long = 0,
        @SerializedName("sizeText") val sizeText: String = "",
        @SerializedName("sha256") val sha256: String = "",
        @SerializedName("notes") val notes: String = "",
        @SerializedName("force") val force: Boolean = false,
        @SerializedName("minSupported") val minSupported: Int = 0,
        @SerializedName("url") val url: String = "",
        @SerializedName("publishedAtText") val publishedAtText: String = ""
    )

    /** 检查更新的响应 */
    data class Response(
        @SerializedName("ok") val ok: Boolean = false,
        @SerializedName("update") val update: Boolean = false,
        @SerializedName("force") val force: Boolean = false,
        @SerializedName("latest") val latest: Release? = null,
        @SerializedName("message") val message: String = "",
        @SerializedName("serverTime") val serverTime: String = ""
    )

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun installedVersionCode(ctx: Context): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        0
    }

    fun installedVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
    } catch (e: Exception) {
        "-"
    }

    fun checkUrl(ctx: Context): String =
        UrlUtils.join(ServerConfig.siteBase(), "apk/ota/api/check") +
            "?vc=" + installedVersionCode(ctx) +
            "&ch=stable&p=" + ctx.packageName

    /** 阻塞式检查，调用方必须在 IO 线程执行 */
    @Throws(IOException::class)
    fun check(ctx: Context): Response {
        val req = Request.Builder()
            .url(checkUrl(ctx))
            .header("Accept", "application/json")
            .header("User-Agent", "shine-app/" + installedVersionName(ctx))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)
            if (body.isBlank()) throw IOException("服务端返回为空")
            return gson.fromJson(body, Response::class.java) ?: throw IOException("响应无法解析")
        }
    }

    /** 只接受同站点 https 的安装包地址，防止被引导到第三方 */
    fun isTrustedUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = try {
            java.net.URI(url).host ?: return false
        } catch (e: Exception) {
            return false
        }
        val siteHost = try {
            java.net.URI(ServerConfig.siteBase()).host ?: return false
        } catch (e: Exception) {
            return false
        }
        return host.equals(siteHost, ignoreCase = true)
    }

    fun updateDir(ctx: Context): File = File(ctx.filesDir, "updates").apply { mkdirs() }

    /**
     * 下载安装包，边下边回报百分比；返回校验通过的本地文件。
     *
     * 支持断点续传：先写到 `.part`，中断后再进来用 Range 从断点继续。
     * 之前是每次开头就把已下载的文件删掉，20MB 的包在弱网上断了只能从头再来。
     */
    @Throws(IOException::class)
    fun download(ctx: Context, rel: Release, onProgress: (Int) -> Unit): File {
        if (!isTrustedUrl(rel.url)) throw IOException("安装包地址不受信任")

        val dir = updateDir(ctx)
        val target = File(dir, "shine-" + rel.versionCode + ".apk")
        val part = File(dir, target.name + ".part")

        // 只清理其它版本的残留，本版本的 .part 要留着续传
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != part.name && f.name != target.name &&
                (f.name.endsWith(".apk") || f.name.endsWith(".part"))
            ) {
                f.delete()
            }
        }

        // 已经下完并校验过的，直接复用
        if (target.exists() && target.length() > 0 && rel.size > 0 && target.length() == rel.size) {
            if (rel.sha256.isBlank() || sha256(target).equals(rel.sha256, ignoreCase = true)) {
                onProgress(100)
                return target
            }
            target.delete()
        }

        var resumeFrom = if (part.exists()) part.length() else 0L
        val totalHint = if (rel.size > 0) rel.size else 0L
        // 断点文件比目标还大，说明对不上，重下
        if (totalHint > 0 && resumeFrom >= totalHint) {
            part.delete()
            resumeFrom = 0L
        }

        val builder = Request.Builder().url(rel.url).header("Accept", "*/*")
        if (resumeFrom > 0) builder.header("Range", "bytes=" + resumeFrom + "-")

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)

            val partial = resp.code == 206
            if (!partial) resumeFrom = 0L // 服务端不支持续传，从头写

            val body = resp.body ?: throw IOException("响应为空")
            val remain = if (body.contentLength() > 0) body.contentLength() else 0L
            val total = when {
                totalHint > 0 -> totalHint
                partial -> resumeFrom + remain
                else -> remain
            }

            var done = resumeFrom
            var last = -1
            if (total > 0 && done > 0) {
                last = (done * 100 / total).toInt()
                onProgress(last)
            }

            body.byteStream().use { input ->
                java.io.FileOutputStream(part, resumeFrom > 0).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
        }

        if (part.length() <= 0) {
            part.delete()
            throw IOException("下载内容为空")
        }
        if (rel.size > 0 && part.length() != rel.size) {
            val got = part.length()
            // 没下完：保留 .part 供下次续传，只提示
            if (got < rel.size) throw IOException("下载中断（" + got + " / " + rel.size + "），已保留进度，可重试继续")
            part.delete()
            throw IOException("安装包大小不一致（" + got + " / " + rel.size + "），请重试")
        }
        if (rel.sha256.isNotBlank()) {
            val actual = sha256(part)
            if (!actual.equals(rel.sha256, ignoreCase = true)) {
                part.delete()
                throw IOException("安装包校验失败，已删除，请重试")
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        onProgress(100)
        return target
    }

    /** 是否有可续传的断点（给界面显示「继续下载」用） */
    fun partialSize(ctx: Context, versionCode: Int): Long {
        val part = File(updateDir(ctx), "shine-" + versionCode + ".apk.part")
        return if (part.exists()) part.length() else 0L
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val sb = StringBuilder()
        for (b in md.digest()) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** 更新说明按行拆开，便于界面逐条排版 */
    fun noteLines(notes: String): List<String> =
        notes.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}
