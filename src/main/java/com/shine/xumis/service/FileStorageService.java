package com.shine.xumis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件存储服务：将客户上传的参考图保存到本地磁盘目录。
 * 生产环境可替换为对象存储（S3 / OSS / COS），只需实现同一接口。
 */
@Service
public class FileStorageService {

    /** 上传根目录，默认 ./uploads，可通过 application.yml 的 app.upload-dir 覆盖 */
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 保存上传文件，返回可访问的相对 URL（/files/xxx）
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        // 用 UUID 重命名，避免中文/特殊字符与重名冲突
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(filename);
        Files.copy(file.getInputStream(), target);
        return "/files/" + filename;
    }
}
