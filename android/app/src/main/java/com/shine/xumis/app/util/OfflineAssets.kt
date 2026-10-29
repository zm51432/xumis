package com.shine.xumis.app.util

import android.content.Context
import android.net.Uri

/**
 * 随 APK 一起安装的离线资源（assets/offline）。
 *
 * 这不是「占位图」式的装饰：断网 / 弱网 / 后端重启时，App 仍然能给出
 * 商品册、配送条款与可操作的错误提示，而不是一张白屏。
 */
object OfflineAssets {

    private const val DIR = "offline"
    private const val IMG_DIR = "$DIR/img"

    const val INDEX = "file:///android_asset/$DIR/index.html"
    const val CATALOG = "file:///android_asset/$DIR/catalog.html"
    const val POLICIES = "file:///android_asset/$DIR/policies.html"

    /**
     * 离线兜底页，带上原目标地址，页面里的「重新连接」会直接回到该地址。
     * reason: timeout / http / offline（仅影响文案）
     */
    fun indexUrl(target: String?, reason: String = ""): String {
        if (target.isNullOrBlank()) return INDEX
        val sb = StringBuilder(INDEX).append("?target=").append(Uri.encode(target))
        if (reason.isNotBlank()) sb.append("&reason=").append(reason)
        return sb.toString()
    }

    /** 内置商品图数量 */
    fun imageCount(ctx: Context): Int = try {
        ctx.assets.list(IMG_DIR)?.count { it.endsWith(".png", true) } ?: 0
    } catch (e: Exception) {
        0
    }

    /** 内置商品图总字节数（用于「离线资源」统计展示） */
    fun imageBytes(ctx: Context): Long {
        var total = 0L
        try {
            ctx.assets.list(IMG_DIR)?.forEach { name ->
                val path = "$IMG_DIR/$name"
                total += try {
                    ctx.assets.openFd(path).use { it.length }
                } catch (e: Exception) {
                    ctx.assets.open(path).use { input ->
                        val buf = ByteArray(8 * 1024)
                        var n = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            n += r
                        }
                        n
                    }
                }
            }
        } catch (e: Exception) {
            // 读取失败不影响主流程
        }
        return total
    }

    fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%d KB", bytes / 1024)
    }
}
