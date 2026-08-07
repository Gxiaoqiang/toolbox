package com.toolbox.service.ocr;

import java.awt.image.BufferedImage;

/**
 * OCR 引擎统一接口 — V1.0 仅 TesseractOcrEngine，后续可扩展 PaddleOCR / 云 API。
 * <p>
 * 注意：入参为 {@link BufferedImage} 而非 byte[]，以彻底绕开 javax.imageio.ImageIO。
 * Spring Boot fat jar 打包后 ServiceLoader 无法加载嵌套 jar 的 imageio SPI（jai-imageio），
 * 会导致 ImageIO 初始化失败；直接传 BufferedImage 则无需任何图片字节编解码。
 *
 * @author toolbox
 * @since 2026-08-04
 */
public interface OcrEngine {

    /**
     * 对单张图片执行 OCR 识别
     *
     * @param image    图片（从 PDF 页面渲染，300 DPI BufferedImage）
     * @param language 识别语言（chi_sim / eng / chi_sim+eng）
     * @return 识别结果：文本 + 词级包围盒
     */
    OcrResult recognize(BufferedImage image, String language);

    /**
     * 引擎名称（用于日志和统计）
     */
    String getEngineName();

    /**
     * 引擎是否可用（检查底层依赖是否就绪）
     */
    boolean isAvailable();
}
