package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.model.pdf.DewatermarkRequest;
import com.toolbox.model.pdf.DewatermarkRequest.RegionItem;
import com.toolbox.model.pdf.DewatermarkResult;
import com.toolbox.service.pdf.impl.PdfDewatermarkServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfDewatermarkService 单元测试
 *
 * @author toolbox
 * @since 2026-08-01
 */
@DisplayName("PDF 去水印服务")
class PdfDewatermarkServiceTest {

    /** 默认 Letter 页高 792pt */
    private static final float PAGE_HEIGHT = 792;

    private PdfDewatermarkService service;

    @BeforeEach
    void setUp() {
        service = new PdfDewatermarkServiceImpl();
    }

    // ===== 辅助方法 =====

    /**
     * 创建一个测试 PDF：含正文文字 + 居中文字水印 + 底部图片水印
     * 坐标均为前端左上角原点（与前端一致），由服务端翻转为 PDF 坐标
     */
    private byte[] createWatermarkedPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // 正文（PDF 底部坐标，near top）
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                cs.newLineAtOffset(100, 700);
                cs.showText("Body Content");
                cs.endText();

                // 文字水印 CONFIDENTIAL（页面中部）
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 30);
                cs.newLineAtOffset(200, 350);
                cs.showText("CONFIDENTIAL");
                cs.endText();

                // 图片水印（页面底部）
                cs.drawImage(createLogoImage(doc), 50, 50, 200, 100);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 生成一个小的红色方块 PNG 作为图片水印 */
    private PDImageXObject createLogoImage(PDDocument doc) throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 20; y++) {
                img.setRGB(x, y, 0xFF0000);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return PDImageXObject.createFromByteArray(doc, out.toByteArray(), "logo");
    }

    /** 从结果 PDF 提取指定页范围的文本 */
    private String extractText(byte[] pdfBytes, int startPage, int endPage) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            return stripper.getText(doc);
        }
    }

    private DewatermarkRequest regionReq(String applyTo, RegionItem... regions) {
        return new DewatermarkRequest(applyTo, Arrays.asList(regions));
    }

    // ===== 参数校验测试 =====

    @Nested
    @DisplayName("参数校验")
    class ParamValidation {

        @Test
        @DisplayName("regions 为空 → 抛 REGIONS_EMPTY")
        void emptyRegions_throwsRegionsEmpty() throws Exception {
            byte[] pdf = createWatermarkedPdf();
            assertThatThrownBy(() -> service.dewatermark(pdf, "t.pdf",
                    new DewatermarkRequest("all", Collections.emptyList())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_DEWATERMARK_REGIONS_EMPTY.getCode());
        }

        @Test
        @DisplayName("applyTo 非法 → 抛 APPLY_INVALID")
        void invalidApplyTo_throwsApplyInvalid() throws Exception {
            byte[] pdf = createWatermarkedPdf();
            assertThatThrownBy(() -> service.dewatermark(pdf, "t.pdf",
                    new DewatermarkRequest("foo", Collections.singletonList(new RegionItem(0, 0, 0, 50, 50)))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_DEWATERMARK_APPLY_INVALID.getCode());
        }
    }

    // ===== 去水印功能测试 =====

    @Nested
    @DisplayName("去水印功能")
    class DewatermarkFunction {

        @Test
        @DisplayName("框选文字+图片水印 → 全部删除，正文保留")
        void boxTextAndImageWatermark_removesBothKeepsBody() throws Exception {
            byte[] pdf = createWatermarkedPdf();

            // 文字水印区域（前端左上角坐标）：CONFIDENTIAL 在 PDF(200,350)，页面高 792
            RegionItem textRegion = new RegionItem(0, 190, PAGE_HEIGHT - 350 - 30, 220, 40);
            // 图片水印区域：drawImage(50,50,200,100) → 左上角 (50, 792-50-100)
            RegionItem imgRegion = new RegionItem(0, 40, PAGE_HEIGHT - 50 - 100, 220, 120);

            DewatermarkResult result = service.dewatermark(pdf, "wm.pdf",
                    regionReq("all", textRegion, imgRegion));

            // 区域级结果：两个都 removed
            assertThat(result.getRemoved()).hasSize(2);
            assertThat(result.getFailed()).isEmpty();
            assertThat(result.getPdfBase64()).isNotBlank();

            // base64 可还原为合法 PDF
            byte[] outPdf = Base64.getDecoder().decode(result.getPdfBase64());
            assertThat(outPdf.length).isGreaterThan(0);

            // 正文保留，文字水印删除
            String text = extractText(outPdf, 1, 1);
            assertThat(text).contains("Body Content");
            assertThat(text).doesNotContain("CONFIDENTIAL");
        }

        @Test
        @DisplayName("框选空白区域 → 该区域上报 failed，正文与水印均保留")
        void boxEmptyArea_reportsFailed() throws Exception {
            byte[] pdf = createWatermarkedPdf();

            // 页面右上角空白区域（无内容）
            RegionItem emptyRegion = new RegionItem(0, 500, 50, 80, 60);

            DewatermarkResult result = service.dewatermark(pdf, "wm.pdf",
                    regionReq("all", emptyRegion));

            assertThat(result.getRemoved()).isEmpty();
            assertThat(result.getFailed()).hasSize(1);

            // 未误删正文与水印
            String text = extractText(Base64.getDecoder().decode(result.getPdfBase64()), 1, 1);
            assertThat(text).contains("Body Content");
            assertThat(text).contains("CONFIDENTIAL");
        }

        @Test
        @DisplayName("applyTo=page 只处理指定页，不影响其它页")
        void pageApply_onlyAffectsSpecifiedPage() throws Exception {
            byte[] pdf = createWatermarkedPdf();
            // 在第二页加一个水印
            byte[] twoPagePdf;
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                PDPage page2 = new PDPage();
                doc.addPage(page2);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    cs.newLineAtOffset(100, 700);
                    cs.showText("Page2 Body");
                    cs.endText();
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 30);
                    cs.newLineAtOffset(200, 350);
                    cs.showText("CONFIDENTIAL");
                    cs.endText();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                twoPagePdf = out.toByteArray();
            }

            // 只去第 2 页（page=1）的水印
            RegionItem region = new RegionItem(1, 190, PAGE_HEIGHT - 350 - 30, 220, 40);
            DewatermarkResult result = service.dewatermark(twoPagePdf, "wm2.pdf",
                    regionReq("page", region));

            assertThat(result.getRemoved()).hasSize(1);
            assertThat(result.getFailed()).isEmpty();

            byte[] outPdf = Base64.getDecoder().decode(result.getPdfBase64());
            // 第 1 页（page=0）未处理：水印仍在
            String page1Text = extractText(outPdf, 1, 1);
            assertThat(page1Text).contains("CONFIDENTIAL");
            assertThat(page1Text).contains("Body Content");
            // 第 2 页（page=1）水印已删除、正文保留
            String page2Text = extractText(outPdf, 2, 2);
            assertThat(page2Text).contains("Page2 Body");
            assertThat(page2Text).doesNotContain("CONFIDENTIAL");
        }
    }
}
