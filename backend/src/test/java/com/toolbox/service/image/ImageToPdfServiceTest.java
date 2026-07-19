package com.toolbox.service.image;

import com.toolbox.service.image.impl.ImageToPdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ImageToPdfService 单元测试
 *
 * @author toolbox
 * @since 2026-07-19
 */
@DisplayName("图片转 PDF 服务")
class ImageToPdfServiceTest {

    private ImageToPdfService service;

    @BeforeEach
    void setUp() {
        service = new ImageToPdfServiceImpl();
    }

    // ===== 辅助方法 =====

    /** 创建指定尺寸的测试图片字节数组 */
    private byte[] createTestImage(int width, int height, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // 填充不同颜色便于区分
        img.getGraphics().fillRect(0, 0, width, height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** 创建 JPEG 测试图片 */
    private byte[] createJpeg(int width, int height) throws Exception {
        return createTestImage(width, height, "jpg");
    }

    /** 创建 PNG 测试图片 */
    private byte[] createPng(int width, int height) throws Exception {
        return createTestImage(width, height, "png");
    }

    /** 加载 PDF 并返回页数 */
    private int countPages(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    /** 获取指定页的 MediaBox */
    private PDRectangle getPageMediaBox(byte[] pdfBytes, int pageIndex) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getPage(pageIndex).getMediaBox();
        }
    }

    // ===== 基础功能测试 =====

    @Nested
    @DisplayName("基础转换")
    class BasicConversion {

        @Test
        @DisplayName("单张 JPEG 转 PDF — 页数为 1")
        void singleJpeg() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "small", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("单张 PNG 转 PDF — 页数为 1")
        void singlePng() throws Exception {
            byte[] img = createPng(1024, 768);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".png"), "portrait", "small", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("多张图片合并 — 页数等于图片数")
        void multipleImages() throws Exception {
            byte[] img1 = createJpeg(800, 600);
            byte[] img2 = createPng(1024, 768);
            byte[] img3 = createJpeg(640, 480);

            byte[] pdf = service.convertToPdf(
                    List.of(img1, img2, img3),
                    List.of(".jpg", ".png", ".jpg"),
                    "portrait", "small", "contain");

            assertThat(countPages(pdf)).isEqualTo(3);
        }
    }

    // ===== 方向测试 =====

    @Nested
    @DisplayName("页面方向")
    class Orientation {

        @Test
        @DisplayName("portrait — 页面宽度 < 高度")
        void portrait() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "none", "contain");

            PDRectangle box = getPageMediaBox(pdf, 0);
            assertThat(box.getWidth()).isEqualTo(PDRectangle.A4.getWidth());   // 595
            assertThat(box.getHeight()).isEqualTo(PDRectangle.A4.getHeight()); // 842
        }

        @Test
        @DisplayName("landscape — 页面宽度 > 高度")
        void landscape() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "landscape", "none", "contain");

            PDRectangle box = getPageMediaBox(pdf, 0);
            assertThat(box.getWidth()).isEqualTo(PDRectangle.A4.getHeight()); // 842
            assertThat(box.getHeight()).isEqualTo(PDRectangle.A4.getWidth()); // 595
        }
    }

    // ===== 边距测试 =====

    @Nested
    @DisplayName("页面边距")
    class Margin {

        @Test
        @DisplayName("none — 可绘制区域 = 整个页面")
        void noneMargin() throws Exception {
            byte[] img = createJpeg(800, 600);
            // none 边距不裁剪，图片撑满页面
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "none", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("small — 36pt 边距")
        void smallMargin() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "small", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("large — 72pt 边距")
        void largeMargin() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "large", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }
    }

    // ===== 适配方式测试 =====

    @Nested
    @DisplayName("适配方式")
    class FitMode {

        @Test
        @DisplayName("contain — 等比缩放，完整显示，不裁剪")
        void contain() throws Exception {
            // 宽图：宽度远大于高度
            byte[] img = createJpeg(1600, 400);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "none", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("cover — 等比缩放，填满页面，可能裁剪")
        void cover() throws Exception {
            // 窄图：高度远大于宽度
            byte[] img = createJpeg(200, 1600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "none", "cover");

            assertThat(countPages(pdf)).isEqualTo(1);
        }

        @Test
        @DisplayName("stretch — 拉伸填满，可能变形")
        void stretch() throws Exception {
            byte[] img = createJpeg(800, 600);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "none", "stretch");

            assertThat(countPages(pdf)).isEqualTo(1);
        }
    }

    // ===== GIF 测试 =====

    @Nested
    @DisplayName("GIF 支持")
    class GifSupport {

        @Test
        @DisplayName("GIF 图片取第一帧转 PDF")
        void gifFirstFrame() throws Exception {
            // 创建一个简单的 GIF 图片
            BufferedImage gifImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            gifImg.getGraphics().fillRect(0, 0, 100, 100);
            ByteArrayOutputStream gifOut = new ByteArrayOutputStream();
            ImageIO.write(gifImg, "gif", gifOut);
            byte[] gifBytes = gifOut.toByteArray();

            byte[] pdf = service.convertToPdf(
                    List.of(gifBytes), List.of(".gif"), "portrait", "small", "contain");

            assertThat(countPages(pdf)).isEqualTo(1);
        }
    }

    // ===== 组合测试 =====

    @Nested
    @DisplayName("组合场景")
    class Combined {

        @Test
        @DisplayName("多张图片 + 混合格式 + landscape + large + cover")
        void mixedScenario() throws Exception {
            byte[] jpg = createJpeg(800, 600);
            byte[] png = createPng(1024, 768);

            byte[] pdf = service.convertToPdf(
                    List.of(jpg, png),
                    List.of(".jpg", ".png"),
                    "landscape", "large", "cover");

            assertThat(countPages(pdf)).isEqualTo(2);

            // 验证是 landscape A4
            PDRectangle box = getPageMediaBox(pdf, 0);
            assertThat(box.getWidth()).isEqualTo(PDRectangle.A4.getHeight());
            assertThat(box.getHeight()).isEqualTo(PDRectangle.A4.getWidth());
        }

        @Test
        @DisplayName("输出 PDF 是有效文件——可被 pdfbox 重新加载")
        void outputIsValidPdf() throws Exception {
            byte[] img = createJpeg(400, 300);
            byte[] pdf = service.convertToPdf(
                    List.of(img), List.of(".jpg"), "portrait", "small", "contain");

            // 重新加载验证不抛异常
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertThat(doc.getNumberOfPages()).isEqualTo(1);
            }
        }
    }
}
