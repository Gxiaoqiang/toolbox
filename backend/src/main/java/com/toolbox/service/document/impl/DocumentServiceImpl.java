package com.toolbox.service.document.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * LibreOffice headless 文档转换服务实现
 *
 * @author toolbox
 * @since 2026-07-10
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentServiceImpl.class);
    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public boolean isServiceAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("soffice", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.warn("LibreOffice 不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] convertToPdf(byte[] fileBytes, String originalFilename) {
        // 检查 soffice 可用
        if (!isServiceAvailable()) {
            throw new BusinessException(ErrorCodeEnum.DOC_SERVICE_UNAVAILABLE);
        }

        // 检查空文件
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCodeEnum.DOC_FILE_EMPTY);
        }

        Path tempDir = null;
        Path inputFile = null;
        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("doc2pdf-");
            inputFile = tempDir.resolve(originalFilename);

            // 写入输入文件
            Files.write(inputFile, fileBytes);

            // 调用 soffice 转换
            ProcessBuilder pb = new ProcessBuilder(
                    "soffice",
                    "--headless",
                    "--norestore",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toString(),
                    inputFile.toString()
            );
            pb.redirectErrorStream(true);

            LOGGER.info("开始转换: {}", originalFilename);
            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            // 读取 stderr/stdout 用于诊断
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                LOGGER.error("soffice 返回非零: exit={}, output={}", process.exitValue(), output);
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            // 找到生成的 PDF 文件
            String pdfName = originalFilename.substring(0, originalFilename.lastIndexOf('.')) + ".pdf";
            Path pdfFile = tempDir.resolve(pdfName);

            if (!Files.exists(pdfFile)) {
                LOGGER.error("PDF 文件未生成: {}, soffice output: {}", pdfFile, output);
                throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
            }

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            LOGGER.info("转换成功: {} -> {} bytes", originalFilename, pdfBytes.length);
            return pdfBytes;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("文档转换异常: {}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.DOC_CONVERT_ERROR);
        } finally {
            // 清理临时文件
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }
    }
}
