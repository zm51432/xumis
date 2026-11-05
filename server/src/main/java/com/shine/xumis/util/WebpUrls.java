package com.shine.xumis.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商品图 WebP 变体探测（P1-1）：仅当服务器上传目录里真的存在对应 .webp 时才返回 URL，
 * 避免后台新上传（尚未转换）的图片被 <source> 选中后 404 裂图。
 */
@Component
public class WebpUrls {

    private static volatile Path root;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final String PREFIX = "/files/";

    public WebpUrls(@Value("${app.upload-dir:./uploads}") String dir) {
        root = Paths.get(dir).toAbsolutePath().normalize();
        CACHE.clear();
    }

    public static String variant(String imageUrl, String suffix) {
        if (imageUrl == null || !imageUrl.startsWith(PREFIX) || root == null) return "";
        String key = imageUrl + "|" + suffix;
        String hit = CACHE.get(key);
        if (hit != null) return hit;
        String rel = imageUrl.substring(PREFIX.length());
        int q = rel.indexOf('?');
        if (q >= 0) rel = rel.substring(0, q);
        int dot = rel.lastIndexOf('.');
        if (dot < 0) { CACHE.put(key, ""); return ""; }
        String ext = rel.substring(dot).toLowerCase();
        if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png")) {
            CACHE.put(key, ""); return "";
        }
        String webRel = rel.substring(0, dot) + suffix + ".webp";
        String result = Files.isRegularFile(root.resolve(webRel)) ? PREFIX + webRel : "";
        CACHE.put(key, result);
        return result;
    }

    /** 供后台批量转图后清缓存（不重启也能立即用上新图） */
    public static void clearCache() { CACHE.clear(); }
}
