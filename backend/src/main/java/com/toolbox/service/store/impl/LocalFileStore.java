package com.toolbox.service.store.impl;

import com.toolbox.service.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * 本地磁盘文件存储 — FileStore 默认实现
 *
 * @author toolbox
 * @since 2026-07-16
 */
public class LocalFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStore.class);

    private final Path uploadDir;
    private final long maxFileSize;
    private final Duration ttl;

    public LocalFileStore(String uploadDirPath, long maxFileSize, Duration ttl) {
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
            log.info("[LocalFileStore#init] upload directory created: {}", uploadDir);
        } catch (IOException e) {
            log.error("[LocalFileStore#init] failed to create upload directory: {}", uploadDir, e);
            throw new RuntimeException("无法创建上传目录", e);
        }
    }

    @Override
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
            log.info("[LocalFileStore#store] file stored: {} → {} ({} bytes)",
                    originalName, fileId, file.getSize());
            return fileId;
        } catch (IOException e) {
            log.error("[LocalFileStore#store] failed to store file: {}", originalName, e);
            throw new RuntimeException("文件存储失败", e);
        }
    }

    @Override
    public String store(byte[] data, String filename) {
        String fileId = UUID.randomUUID().toString();
        if (filename.contains(".")) {
            fileId += filename.substring(filename.lastIndexOf('.'));
        }
        Path target = uploadDir.resolve(fileId);
        try {
            Files.write(target, data);
            log.info("[LocalFileStore#store] result stored: {} ({} bytes)", fileId, data.length);
            return fileId;
        } catch (IOException e) {
            log.error("[LocalFileStore#store] failed to store result", e);
            throw new RuntimeException("产物存储失败", e);
        }
    }

    @Override
    public byte[] load(String fileId) {
        File file = uploadDir.resolve(fileId).toFile();
        if (file.exists()) {
            try {
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                throw new RuntimeException("文件读取失败: " + fileId, e);
            }
        }
        // LLM 可能把文件扩展名丢了（如 d34fe0f6.docx → d34fe0f6），在目录中查找匹配
        File[] candidates = uploadDir.toFile()
                .listFiles((dir, name) -> name.startsWith(fileId));
        if (candidates != null && candidates.length > 0) {
            log.info("[LocalFileStore#load] fuzzy match: {} → {}", fileId,
                    candidates[0].getName());
            try {
                return Files.readAllBytes(candidates[0].toPath());
            } catch (IOException e) {
                throw new RuntimeException("文件读取失败: " + candidates[0].getName(), e);
            }
        }
        throw new IllegalArgumentException("文件不存在或已过期: " + fileId);
    }

    @Override
    public void delete(String fileId) {
        try {
            boolean deleted = Files.deleteIfExists(uploadDir.resolve(fileId));
            if (deleted) {
                log.info("[LocalFileStore#delete] file deleted: {}", fileId);
            }
        } catch (IOException e) {
            log.warn("[LocalFileStore#delete] failed to delete file: {}", fileId, e);
        }
    }

    /**
     * 定时清理过期文件
     */
    @Scheduled(initialDelayString = "${toolbox.agent.file.cleanup-interval-minutes:30}",
               fixedRateString = "${toolbox.agent.file.cleanup-interval-minutes:30}",
               timeUnit = java.util.concurrent.TimeUnit.MINUTES)
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
            log.info("[LocalFileStore#cleanup] cleaned {} expired files", count);
        }
    }
}
