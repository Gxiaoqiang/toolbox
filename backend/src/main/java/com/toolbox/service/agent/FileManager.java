package com.toolbox.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * 文件生命周期管理 — 上传存储、读取、定时清理
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class FileManager {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    private final Path uploadDir;
    private final long maxFileSize;
    private final Duration ttl;

    public FileManager(String uploadDirPath, long maxFileSize, Duration ttl) {
        this.uploadDir = Path.of(uploadDirPath);
        this.maxFileSize = maxFileSize;
        this.ttl = ttl;
        init();
    }

    /**
     * 初始化上传目录
     */
    private void init() {
        try {
            Files.createDirectories(uploadDir);
            log.info("[FileManager#init] upload directory created: {}", uploadDir);
        } catch (IOException e) {
            log.error("[FileManager#init] failed to create upload directory: {}", uploadDir, e);
            throw new RuntimeException("无法创建上传目录", e);
        }
    }

    /**
     * 存储上传文件
     *
     * @param file MultipartFile
     * @return fileId 唯一文件标识（含扩展名）
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "文件过大: " + (file.getSize() / (1024 * 1024)) + "MB > " +
                    (maxFileSize / (1024 * 1024)) + "MB");
        }

        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            fileId += originalName.substring(originalName.lastIndexOf('.'));
        }

        Path target = uploadDir.resolve(fileId);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FileManager#store] file stored: {} → {} ({} bytes)",
                    originalName, fileId, file.getSize());
            return fileId;
        } catch (IOException e) {
            log.error("[FileManager#store] failed to store file: {}", originalName, e);
            throw new RuntimeException("文件存储失败", e);
        }
    }

    /**
     * 存储字节数组（处理结果产物）
     *
     * @param data     字节数据
     * @param filename 文件名（含扩展名）
     * @return fileId
     */
    public String storeBytes(byte[] data, String filename) {
        String fileId = UUID.randomUUID().toString();
        if (filename.contains(".")) {
            fileId += filename.substring(filename.lastIndexOf('.'));
        }
        Path target = uploadDir.resolve(fileId);
        try {
            Files.write(target, data);
            log.info("[FileManager#storeBytes] result stored: {} ({} bytes)", fileId, data.length);
            return fileId;
        } catch (IOException e) {
            log.error("[FileManager#storeBytes] failed to store result", e);
            throw new RuntimeException("产物存储失败", e);
        }
    }

    /**
     * 加载存储的文件
     *
     * @param fileId 文件标识
     * @return File 对象
     */
    public File load(String fileId) {
        File file = uploadDir.resolve(fileId).toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在或已过期: " + fileId);
        }
        return file;
    }

    /**
     * 删除指定文件
     *
     * @param fileId 文件标识
     */
    public void delete(String fileId) {
        try {
            boolean deleted = Files.deleteIfExists(uploadDir.resolve(fileId));
            if (deleted) {
                log.info("[FileManager#delete] file deleted: {}", fileId);
            }
        } catch (IOException e) {
            log.warn("[FileManager#delete] failed to delete file: {}", fileId, e);
        }
    }

    /**
     * 清理过期文件（由定时任务调用）
     */
    public void cleanup() {
        File dir = uploadDir.toFile();
        File[] files = dir.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        long ttlMs = ttl.toMillis();
        int count = 0;

        for (File file : files) {
            if (now - file.lastModified() > ttlMs) {
                if (file.delete()) count++;
            }
        }
        if (count > 0) {
            log.info("[FileManager#cleanup] cleaned {} expired files", count);
        }
    }

    public Path getUploadDir() { return uploadDir; }
}
