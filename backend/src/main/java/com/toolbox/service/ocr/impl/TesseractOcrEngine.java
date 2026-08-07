package com.toolbox.service.ocr.impl;

import com.toolbox.service.ocr.OcrEngine;
import com.toolbox.service.ocr.OcrResult;
import com.toolbox.service.ocr.OcrResult.WordBox;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tesseract OCR 引擎实现 — 基于 Tess4J (JNA 直接调用系统 libtesseract)
 * <p>
 * 通过 {@link Tesseract#getWords(BufferedImage, ITessAPI.TessPageIteratorLevel)} 获取词级包围盒，
 * 用于构建可搜索 PDF 的透明文字层。
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TesseractOcrEngine.class);

    /** Tesseract 数据目录（tessdata），默认走系统 TESSDATA_PREFIX */
    @Value("${toolbox.ocr.tessdata-path:}")
    private String tessdataPath;

    /** 识别词级包围盒的迭代级别 */
    private static final int WORD_LEVEL = ITessAPI.TessPageIteratorLevel.RIL_WORD;

    /** 常见 libtesseract 库目录（跨平台探测） */
    private static final List<String> CANDIDATE_LIB_DIRS = List.of(
            "/usr/local/lib",       // macOS Intel Homebrew
            "/opt/homebrew/lib",    // macOS Apple Silicon Homebrew
            "/usr/lib",
            "/usr/lib/x86_64-linux-gnu",
            "/usr/lib/aarch64-linux-gnu",
            "/usr/lib64");

    /** 常见 tessdata 数据目录（跨平台探测） */
    private static final List<String> CANDIDATE_TESSDATA_DIRS = List.of(
            "/usr/local/share/tessdata",   // macOS Homebrew
            "/opt/homebrew/share/tessdata",
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/4.00/tessdata");

    public TesseractOcrEngine() {
        ensureJnaLibraryPath();
    }

    @Override
    public OcrResult recognize(BufferedImage image, String language) {
        Tesseract tesseract = buildTesseract(language);

        try {
            if (image == null) {
                LOGGER.warn("[TesseractOcrEngine#recognize] null image, lang={}", language);
                return new OcrResult("", List.of());
            }

            // 文本
            String text = tesseract.doOCR(image);
            // 词级包围盒（Tess4J getWords 返回的是像素坐标，后续由调用方转换到 PDF 坐标系）
            List<WordBox> boxes = extractWordBoxes(tesseract, image);

            LOGGER.info("[TesseractOcrEngine#recognize] lang={}, chars={}, words={}",
                    language, text.length(), boxes.size());
            return new OcrResult(text, boxes);
        } catch (TesseractException | IllegalStateException e) {
            LOGGER.error("[TesseractOcrEngine#recognize] OCR failed, lang={}", language, e);
            throw new IllegalStateException("OCR 识别失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getEngineName() {
        return "tesseract";
    }

    @Override
    public boolean isAvailable() {
        try {
            // 触发一次最小初始化：构建 Tesseract 实例校验数据目录可访问，失败即视为不可用
            buildTesseract("eng");
            return true;
        } catch (Exception e) {
            LOGGER.warn("[TesseractOcrEngine#isAvailable] engine unavailable: {}", e.getMessage());
            return false;
        }
    }

    // ======================== 内部实现 ========================

    private Tesseract buildTesseract(String language) {
        Tesseract tesseract = new Tesseract();
        String datapath = resolveDatapath();
        if (!datapath.isBlank()) {
            tesseract.setDatapath(datapath);
        }
        // 语言用 + 连接多个（如 chi_sim+eng）
        tesseract.setLanguage(language);
        return tesseract;
    }

    /**
     * 将候选库目录追加到 jna.library.path，使 JNA 能定位 libtesseract
     */
    private static void ensureJnaLibraryPath() {
        Set<String> paths = new LinkedHashSet<>();
        String existing = System.getProperty("jna.library.path");
        if (existing != null && !existing.isBlank()) {
            paths.addAll(List.of(existing.split(File.pathSeparator)));
        }
        for (String dir : CANDIDATE_LIB_DIRS) {
            if (Files.exists(Path.of(dir))) {
                paths.add(dir);
            }
        }
        System.setProperty("jna.library.path", String.join(File.pathSeparator, paths));
    }

    /**
     * 解析 tessdata 数据目录：配置 > 环境变量 TESSDATA_PREFIX > 常见路径
     */
    private String resolveDatapath() {
        if (tessdataPath != null && !tessdataPath.isBlank() && Files.isDirectory(Path.of(tessdataPath))) {
            return tessdataPath;
        }
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank() && Files.isDirectory(Path.of(env))) {
            return env;
        }
        for (String dir : CANDIDATE_TESSDATA_DIRS) {
            if (Files.isDirectory(Path.of(dir))) {
                return dir;
            }
        }
        return "";
    }

    /**
     * 提取词级包围盒（像素坐标）
     */
    private List<WordBox> extractWordBoxes(Tesseract tesseract, BufferedImage image) {
        List<Word> words = tesseract.getWords(List.of(image), WORD_LEVEL);
        List<WordBox> result = new ArrayList<>(words.size());
        for (Word word : words) {
            java.awt.Rectangle rect = word.getBoundingBox();
            if (rect == null || word.getText() == null || word.getText().isBlank()) {
                continue;
            }
            result.add(new WordBox(word.getText(), rect.x, rect.y, rect.width, rect.height));
        }
        return result;
    }
}
