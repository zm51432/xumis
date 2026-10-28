package com.shine.xumis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储服务：将客户上传的参考图保存到本地磁盘目录。
 * 生产环境可替换为对象存储（S3 / OSS / COS），只需实现同一接口。
 */
@Service
public class FileStorageService {

    /** 上传根目录，默认 ./uploads，可通过 application.yml 的 app.upload-dir 覆盖 */
    /** 允许上传的图片扩展名白名单（不含 svg，避免内联脚本风险） */
    private static final Set<String> ALLOWED_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".avif");

    /** 单个文件大小上限（默认 5MB），防止有人塞超大文件撑爆磁盘 */
    @Value("${app.upload-max-bytes:5242880}")
    private long maxBytes;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 保存上传文件，返回可访问的相对 URL（/files/xxx）
     *
     * 校验三重：扩展名白名单 → 大小上限 → 真实文件头（magic bytes）。
     * 只看扩展名是不够的：把脚本改名为 .png 就能绕过。
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "文件过大（" + (file.getSize() / 1024 / 1024) + "MB），上限 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        // 扩展名白名单：拒绝 .jsp/.html/.svg 等可能被当作脚本执行的文件
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException(
                    "不支持的文件类型：" + ext + "，仅允许 " + String.join(" / ", ALLOWED_EXT));
        }
        // 真实内容校验：读取文件头，确认确实是图片而不是"改了后缀的别的东西"
        byte[] head = new byte[12];
        int read;
        try (java.io.InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (!looksLikeImage(head, read)) {
            throw new IllegalArgumentException("文件内容不是合法图片，已拒绝保存");
        }
        // 用 UUID 重命名，避免中文/特殊字符与重名冲突
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(filename);
        Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return "/files/" + filename;
    }

    /**
     * 按文件头判断真实图片类型（PNG / JPEG / GIF / WEBP / BMP / AVIF）。
     * 用文件头而不是扩展名，避免"改后缀"绕过。
     */
    private boolean looksLikeImage(byte[] h, int len) {
        if (len < 4) {
            return false;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return true;
        }
        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return true;
        }
        // GIF: "GIF8"
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return true;
        }
        // BMP: "BM"
        if (h[0] == 'B' && h[1] == 'M') {
            return true;
        }
        // WEBP: "RIFF" .... "WEBP"
        if (len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return true;
        }
        // AVIF / HEIF: .... "ftyp"
        if (len >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return true;
        }
        return false;
    }
}
