package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.WatermarkRequest;
import com.toolbox.service.pdf.impl.PdfWatermarkServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfWatermarkService 单元测试（工单 01：文本水印）
 *
 * @author toolbox
 * @since 2026-08-02
 */
@DisplayName("PDF 添加水印服务")
class PdfWatermarkServiceTest {

    private PdfWatermarkService service;

    @BeforeEach
    void setUp() {
        service = new PdfWatermarkServiceImpl();
    }

    // ===== 辅助方法 =====

    /** 创建带正文的测试 PDF（单页 A4） */
    private byte[] createPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                cs.newLineAtOffset(72, 700);
                cs.showText("Hello Body");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String extractText(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private WatermarkRequest textRequest(String text) {
        WatermarkRequest req = new WatermarkRequest();
        req.setSource("text");
        req.setText(text);
        return req;
    }

    // ===== 参数校验 =====

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("文本为空 → 抛 TEXT_EMPTY")
        void emptyText_throwsTextEmpty() throws Exception {
            byte[] pdf = createPdf();
            assertThatThrownBy(() -> service.addWatermark(pdf, "t.pdf", textRequest("  "), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_WATERMARK_TEXT_EMPTY.getCode());
        }

        @Test
        @DisplayName("来源非法 → 抛 SOURCE_INVALID")
        void invalidSource_throwsSourceInvalid() throws Exception {
            byte[] pdf = createPdf();
            WatermarkRequest req = textRequest("x");
            req.setSource("foo");
            assertThatThrownBy(() -> service.addWatermark(pdf, "t.pdf", req, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_WATERMARK_SOURCE_INVALID.getCode());
        }
    }

    // ===== 文本水印功能 =====

    @Nested
    @DisplayName("文本水印功能")
    class TextWatermark {

        @Test
        @DisplayName("添加中文文字水印 → 页面含文字、正文保留")
        void addTextWatermark_watermarkAddedBodyKept() throws Exception {
            byte[] pdf = createPdf();
            byte[] result = service.addWatermark(pdf, "t.pdf", textRequest("内部资料"), null);

            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(pdf.length);

            String text = extractText(result);
            assertThat(text).contains("内部资料");
            assertThat(text).contains("Hello Body");
        }

        @Test
        @DisplayName("水印渲染到页面中心（渲染该页中心区域有非白像素）")
        void textWatermark_rendersAtCenter() throws Exception {
            byte[] pdf = createPdf();
            byte[] result = service.addWatermark(pdf, "t.pdf", textRequest("机密文件"), null);

            try (PDDocument doc = Loader.loadPDF(result)) {
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage img = renderer.renderImageWithDPI(0, 100);
                // 页面中心 20x20 区域应存在非白像素（水印）
                boolean hasInk = false;
                int cx = img.getWidth() / 2;
                int cy = img.getHeight() / 2;
                for (int x = cx - 10; x < cx + 10; x++) {
                    for (int y = cy - 10; y < cy + 10; y++) {
                        int rgb = img.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        if (r < 200 || g < 200 || b < 200) {
                            hasInk = true;
                        }
                    }
                }
                assertThat(hasInk).as("页面中心应渲染出水印像素").isTrue();
            }
        }
    }
}
