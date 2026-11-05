package com.shine.xumis.app.util

import android.content.Context

/**
 * 随 APK 一起安装的离线资源（assets/offline）。
 *
 * App 已移除 WebView，离线内容改为原生页面：
 *  · 离线商品册 → CatalogActivity（本机缓存的商品用原生网格展示）
 *  · 配送与售后条款 → DocActivity（内置纯文本）
 * 这里只保留内置商品图的统计能力（设置页 / 关于页展示用）。
 */
object OfflineAssets {

    private const val DIR = "offline"
    private const val IMG_DIR = "$DIR/img"

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
