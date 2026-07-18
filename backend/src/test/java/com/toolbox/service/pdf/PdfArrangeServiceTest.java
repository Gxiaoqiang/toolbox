package com.toolbox.service.pdf;

import com.toolbox.model.common.PdfArrangeItem;
import com.toolbox.service.pdf.impl.PdfArrangeServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfArrangeService 单元测试
 *
 * @author toolbox
 * @since 2026-07-18
 */
@DisplayName("PDF 编排服务")
class PdfArrangeServiceTest {

    private PdfArrangeService service;

    @BeforeEach
    void setUp() {
        service = new PdfArrangeServiceImpl();
    }

    /** 生成一个包含指定页数的 PDF 字节数组 */
    private byte[] emptyPdf(int pageCount) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private int countPages(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    // ===================== 基础功能测试 =====================

    @Test
    @DisplayName("单文件无变动编排后页数一致")
    void singleFileNoChange() throws Exception {
        byte[] pdf = emptyPdf(3);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.fromFile(0, 2),
                PdfArrangeItem.fromFile(0, 3)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        assertThat(countPages(result)).isEqualTo(3);
    }

    @Test
    @DisplayName("删除中间页后页数减少")
    void removeMiddlePage() throws Exception {
        byte[] pdf = emptyPdf(3);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.fromFile(0, 3)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        assertThat(countPages(result)).isEqualTo(2);
    }

    @Test
    @DisplayName("复制页：同一源页出现两次")
    void duplicatePage() throws Exception {
        byte[] pdf = emptyPdf(3);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.fromFile(0, 2),
                PdfArrangeItem.fromFile(0, 2),
                PdfArrangeItem.fromFile(0, 3)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        assertThat(countPages(result)).isEqualTo(4);
    }

    @Test
    @DisplayName("跨文件合并——从两个 PDF 各取页面")
    void crossFileMerge() throws Exception {
        byte[] pdfA = emptyPdf(2);
        byte[] pdfB = emptyPdf(3);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.fromFile(1, 2),
                PdfArrangeItem.fromFile(1, 3),
                PdfArrangeItem.fromFile(0, 2)
        );

        byte[] result = service.arrange(List.of(pdfA, pdfB), plan);

        assertThat(countPages(result)).isEqualTo(4);
    }

    @Test
    @DisplayName("插入空白页")
    void insertBlankPage() throws Exception {
        byte[] pdf = emptyPdf(2);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.newBlank(),
                PdfArrangeItem.fromFile(0, 2)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        assertThat(countPages(result)).isEqualTo(3);
        try (PDDocument doc = Loader.loadPDF(result)) {
            PDRectangle mediaBox = doc.getPage(1).getMediaBox();
            assertThat(mediaBox.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(mediaBox.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
        }
    }

    @Test
    @DisplayName("空白页指定尺寸")
    void blankPageWithCustomSize() throws Exception {
        byte[] pdf = emptyPdf(1);
        float w = 200f, h = 300f;
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1),
                new PdfArrangeItem(null, null, 0, true, w, h)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        try (PDDocument doc = Loader.loadPDF(result)) {
            PDRectangle mediaBox = doc.getPage(1).getMediaBox();
            assertThat(mediaBox.getWidth()).isEqualTo(w);
            assertThat(mediaBox.getHeight()).isEqualTo(h);
        }
    }

    @Test
    @DisplayName("旋转页面 90 度")
    void rotatePage90() throws Exception {
        byte[] pdf = emptyPdf(1);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1, 90)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        try (PDDocument doc = Loader.loadPDF(result)) {
            assertThat(doc.getPage(0).getRotation()).isEqualTo(90);
        }
    }

    @Test
    @DisplayName("反转第一页顺序")
    void reversePageOrder() throws Exception {
        byte[] pdf = emptyPdf(3);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 3),
                PdfArrangeItem.fromFile(0, 2),
                PdfArrangeItem.fromFile(0, 1)
        );

        byte[] result = service.arrange(List.of(pdf), plan);

        assertThat(countPages(result)).isEqualTo(3);
    }

    // ===================== 校验测试 =====================

    @Test
    @DisplayName("plan 为空时抛出异常")
    void emptyPlanThrows() throws Exception {
        assertThatThrownBy(() -> service.arrange(List.of(emptyPdf(1)), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("编排计划不能为空");
    }

    @Test
    @DisplayName("plan 文件下标越界时抛出异常")
    void fileIndexOutOfBoundsThrows() throws Exception {
        byte[] pdf = emptyPdf(2);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(2, 1)
        );

        assertThatThrownBy(() -> service.arrange(List.of(pdf), plan))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("源文件");
    }

    @Test
    @DisplayName("plan 页码越界时抛出异常")
    void pageOutOfRangeThrows() throws Exception {
        byte[] pdf = emptyPdf(2);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 5)
        );

        assertThatThrownBy(() -> service.arrange(List.of(pdf), plan))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("页码范围");
    }

    @Test
    @DisplayName("plan rotate 非 90 倍数时抛出异常")
    void invalidRotateThrows() throws Exception {
        byte[] pdf = emptyPdf(1);
        List<PdfArrangeItem> plan = List.of(
                PdfArrangeItem.fromFile(0, 1, 45)
        );

        assertThatThrownBy(() -> service.arrange(List.of(pdf), plan))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("旋转度数");
    }
}
