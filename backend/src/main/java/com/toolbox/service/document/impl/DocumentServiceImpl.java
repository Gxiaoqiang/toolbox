package com.toolbox.service.document.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * LibreOffice headless 文档转换服务实现
 *
 * @author toolbox
 * @since 2026-07-10
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentServiceImpl.class);

    /** 单次转换超时（秒） */
    private static final long TIMEOUT_SECONDS = 60;

    /** 并发上限：可通过 toolbox.libreoffice.max-concurrent 配置，默认 10 */
    private final int maxConcurrent;

    /** 工作目录 — 所有临时文件统一在此，便于管理和清理 */
    private static final Path WORK_DIR = Path.of(System.getProperty("java.io.tmpdir"), "toolbox-doc2pdf");

    /** 残留目录清理阈值：超过此时间的目录视为残留（1 小时） */
    private static final long ORPHAN_THRESHOLD_MS = 3_600_000;

    /** LibreOffice 优化后的用户配置目录（Docker 构建时预置） */
    private static final String LO_PROFILE = "/opt/lo-profile";

    /** 输入格式过滤器映射 */
    private static final java.util.Map<String, String> INFILTER_MAP = java.util.Map.of(
            "docx", "MS Word 2007 XML",
            "doc", "MS Word 97"
    );

    private final String sofficeBinary;

    /** soffice 可用性缓存：启动后检查一次，避免每次请求都 fork --version */
    private final AtomicBoolean serviceAvailable = new AtomicBoolean(false);
    private volatile boolean availabilityChecked;

    /** 并发信号量 */
    private final Semaphore semaphore;

    /**
     * @param sofficeBinary soffice 二进制路径
     * @param maxConcurrent 最大并发转换数，默认 10
     */
    public DocumentServiceImpl(
            @Value("${toolbox.libreoffice.binary-path:soffice}") String sofficeBinary,
            @Value("${toolbox.libreoffice.max-concurrent:10}") int maxConcurrent) {
        this.sofficeBinary = sofficeBinary;
        this.maxConcurrent = maxConcurrent;
        this.semaphore = new Semaphore(maxConcurrent, true);
        LOGGER.info("[DocumentServiceImpl] soffice path: {}, max concurrent: {}",
                sofficeBinary, maxConcurrent);
    }

    // ======================== 公开方法 ========================

    @Override
    public boolean isServiceAvailable() {
        if (!availabilityChecked) {
            synchronized (this) {
                if (!availabilityChecked) {
                    serviceAvailable.set(checkSoffice());
                    availabilityChecked = true;
                    LOGGER.info("[DocumentServiceImpl#isServiceAvailable] available: {}",
                            serviceAvailable.get());
                }
            }
        }
        return serviceAvailable.get();
    }

    @Override
    public byte[] convertToPdf(byte[] fileBytes, String originalFilename) {
        if (!isServiceAvailable()) {
            throw new BusinessException(ErrorCodeEnum.DOC_SERVICE_UNAVAILABLE);
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_EMPTY);
        }

        // 获取并发许可，避免无限制 fork soffice 进程
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
        }
        if (!acquired) {
            LOGGER.warn("[DocumentServiceImpl#convertToPdf] semaphore exhausted, request rejected");
            throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
        }

        try {
            return doConvert(fileBytes, originalFilename);
        } finally {
            semaphore.release();
        }
    }

    /**
     * 定时清理残留临时目录（兜底机制）
     * JVM 被 kill -9 或崩溃时 finally 块不会执行，残留目录靠此定时任务回收
     */
    @Scheduled(fixedRate = ORPHAN_THRESHOLD_MS)
    public void cleanupOrphanedDirs() {
        if (!Files.exists(WORK_DIR)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - ORPHAN_THRESHOLD_MS;
        try (Stream<Path> stream = Files.list(WORK_DIR)) {
            stream.forEach(dir -> {
                try {
                    if (Files.getLastModifiedTime(dir).toMillis() < cutoff) {
                        deleteDir(dir);
                        LOGGER.info("[DocumentServiceImpl#cleanupOrphanedDirs] cleaned: {}",
                                dir.getFileName());
                    }
                } catch (IOException e) {
                    LOGGER.warn("[DocumentServiceImpl#cleanupOrphanedDirs] skip: {}", dir, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("[DocumentServiceImpl#cleanupOrphanedDirs] list error", e);
        }
    }

    // ======================== 私有方法 ========================

    private boolean checkSoffice() {
        try {
            ProcessBuilder pb = new ProcessBuilder(sofficeBinary, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.warn("[DocumentServiceImpl#checkSoffice] failed: {}", e.getMessage());
            return false;
        }
    }

    private byte[] doConvert(byte[] fileBytes, String originalFilename) {
        Path subDir = null;
        try {
            // 统一工作目录 + 请求级子目录（不同请求互不干扰）
            Files.createDirectories(WORK_DIR);
            subDir = Files.createTempDirectory(WORK_DIR, "req-");
            Path inputFile = subDir.resolve(originalFilename);
            Files.write(inputFile, fileBytes);

            // 构建 soffice 命令（高保真参数）
            String ext = getExtension(originalFilename);
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(sofficeBinary);
            cmd.add("--headless");
            cmd.add("--norestore");
            // 使用预置的优化 profile（字体嵌入 + PDF/A-1b + 无损压缩）
            if (Files.exists(Path.of(LO_PROFILE))) {
                cmd.add("-env:UserInstallation=" + Path.of(LO_PROFILE).toUri());
            }
            // 显式指定输入格式过滤器，避免自动检测误判
            String infilter = INFILTER_MAP.get(ext);
            if (infilter != null) {
                cmd.add("--infilter=" + infilter);
            }
            // 使用 Writer 专用 PDF 导出过滤器（比默认 auto-detect 保真度高）
            cmd.add("--convert-to");
            cmd.add("pdf:writer_pdf_Export");
            cmd.add("--outdir");
            cmd.add(subDir.toString());
            cmd.add(inputFile.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            LOGGER.info("[DocumentServiceImpl#doConvert] converting: {}", originalFilename);
            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                LOGGER.error("[DocumentServiceImpl#doConvert] timeout: {}", originalFilename);
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                LOGGER.error("[DocumentServiceImpl#doConvert] soffice exit={}, output={}",
                        process.exitValue(), output);
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            // 找到 PDF 产物
            String pdfName = originalFilename.substring(0, originalFilename.lastIndexOf('.')) + ".pdf";
            Path pdfFile = subDir.resolve(pdfName);
            if (!Files.exists(pdfFile)) {
                LOGGER.error("[DocumentServiceImpl#doConvert] PDF not found: {}, output={}",
                        pdfFile, output);
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            LOGGER.info("[DocumentServiceImpl#doConvert] success: {} -> {} bytes",
                    originalFilename, pdfBytes.length);
            return pdfBytes;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[DocumentServiceImpl#doConvert] error: {}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
        } finally {
            deleteDir(subDir);
        }
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
