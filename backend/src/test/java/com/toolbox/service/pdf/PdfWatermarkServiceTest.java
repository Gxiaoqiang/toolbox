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

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

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

    /** 创建一个纯色 PNG 图片字节 */
    private byte[] createPng() throws Exception {
        BufferedImage img = new BufferedImage(50, 25, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 50; x++) {
            for (int y = 0; y < 25; y++) {
                img.setRGB(x, y, 0xFFFF0000);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 渲染指定页，检查某个归一化区域是否存在墨迹（水印） */
    private boolean regionHasInk(byte[] pdf, int pageIndex, double nx, double ny) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(pageIndex, 100);
            int cx = (int) (img.getWidth() * nx);
            int cy = (int) (img.getHeight() * ny);
            for (int x = cx - 8; x < cx + 8; x++) {
                for (int y = cy - 8; y < cy + 8; y++) {
                    if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r < 200 || g < 200 || b < 200) return true;
                }
            }
            return false;
        }
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
            assertThat(regionHasInk(result, 0, 0.5, 0.5)).as("页面中心应渲染出水印像素").isTrue();
        }
    }

    // ===== 图片水印 =====

    @Nested
    @DisplayName("图片水印功能")
    class ImageWatermark {

        @Test
        @DisplayName("添加图片水印 → 页面中心渲染、正文保留")
        void addImageWatermark_renderedBodyKept() throws Exception {
            byte[] pdf = createPdf();
            WatermarkRequest req = new WatermarkRequest();
            req.setSource("image");
            byte[] result = service.addWatermark(pdf, "t.pdf", req, createPng());

            assertThat(result).isNotNull();
            assertThat(regionHasInk(result, 0, 0.5, 0.5)).as("页面中心应有图片水印墨迹").isTrue();
            assertThat(extractText(result)).contains("Hello Body");
        }
    }

    // ===== 位置控制 =====

    @Nested
    @DisplayName("位置控制")
    class Position {

        @Test
        @DisplayName("对齐左下角 → 水印出现在左下角而非中心")
        void bottomLeftAlign_watermarkAtBottomLeft() throws Exception {
            byte[] pdf = createPdf();
            WatermarkRequest req = textRequest("机密");
            req.setAlignX("left");
            req.setAlignY("bottom");
            byte[] result = service.addWatermark(pdf, "t.pdf", req, null);

            assertThat(regionHasInk(result, 0, 0.15, 0.1)).as("左下角应渲染水印").isTrue();
            assertThat(regionHasInk(result, 0, 0.5, 0.5)).as("中心不应渲染水印").isFalse();
        }
    }

    // ===== 页面范围 + 子集 =====

    @Nested
    @DisplayName("页面范围与子集")
    class PageRange {

        private byte[] create3PagePdf() throws Exception {
            try (PDDocument doc = new PDDocument()) {
                for (int i = 0; i < 3; i++) {
                    doc.addPage(new PDPage());
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                return out.toByteArray();
            }
        }

        @Test
        @DisplayName("范围1-2 + 子集偶数 → 仅第2页有水印")
        void pageRangeSubset_onlyEvenInRange() throws Exception {
            byte[] pdf = create3PagePdf();
            WatermarkRequest req = textRequest("水印");
            req.setRange("pageRange");
            req.setFromPage(1);
            req.setToPage(2);
            req.setSubset("even");
            byte[] result = service.addWatermark(pdf, "t.pdf", req, null);

            // 第1页无、第2页有、第3页无（超出范围）
            assertThat(regionHasInk(result, 0, 0.5, 0.5)).as("第1页不应有水印").isFalse();
            assertThat(regionHasInk(result, 1, 0.5, 0.5)).as("第2页应有水印").isTrue();
            assertThat(regionHasInk(result, 2, 0.5, 0.5)).as("第3页不应有水印").isFalse();
        }
    }
}
