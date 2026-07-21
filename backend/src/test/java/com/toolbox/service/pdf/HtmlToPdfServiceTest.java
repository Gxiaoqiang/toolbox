package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.impl.AdFilterServiceImpl;
import com.toolbox.service.pdf.impl.HtmlToPdfServiceImpl;
import com.toolbox.service.pdf.impl.StaticAdDomainProvider;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HtmlToPdfService 集成测试
 * 使用真实 Playwright 实例验证端到端渲染
 *
 * @author toolbox
 * @since 2026-07-19
 */
@DisplayName("HTML 转 PDF 服务")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HtmlToPdfServiceTest {

    private static HtmlToPdfService service;

    @BeforeAll
    static void setUp() {
        AdDomainProvider provider = new StaticAdDomainProvider();
        AdFilterService adFilter = new AdFilterServiceImpl(List.of(provider));
        service = new HtmlToPdfServiceImpl(adFilter);
    }

    // ===== URL 转换测试 =====

    @Nested
    @DisplayName("URL 转 PDF")
    class ConvertUrl {

        @Test
        @DisplayName("应该将公开 URL 转换为 PDF")
        @Order(1)
        void shouldConvertPublicUrlToPdf() {
            RenderContext ctx = new RenderContext();
            byte[] pdf = service.convertUrl("https://example.com", ctx);

            assertThat(pdf).isNotNull();
            assertThat(pdf.length).isGreaterThan(0);
            // PDF 文件头: %PDF
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("无效 URL 应抛出 URL_INVALID 异常")
        @Order(2)
        void shouldRejectInvalidUrl() {
            RenderContext ctx = new RenderContext();
            assertThatThrownBy(() -> service.convertUrl("not-a-url", ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID.getCode());
        }

        @Test
        @DisplayName("空 URL 应抛出 URL_EMPTY 异常")
        @Order(3)
        void shouldRejectEmptyUrl() {
            RenderContext ctx = new RenderContext();
            assertThatThrownBy(() -> service.convertUrl("", ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY.getCode());
        }

        @Test
        @DisplayName("null URL 应抛出 URL_EMPTY 异常")
        @Order(4)
        void shouldRejectNullUrl() {
            RenderContext ctx = new RenderContext();
            assertThatThrownBy(() -> service.convertUrl(null, ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY.getCode());
        }
    }

    // ===== HTML 文件转换测试 =====

    @Nested
    @DisplayName("HTML 文件转 PDF")
    class ConvertHtml {

        @Test
        @DisplayName("应该将 HTML 内容转换为 PDF")
        @Order(5)
        void shouldConvertHtmlToPdf() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test</title></head>
                    <body><h1>Hello World</h1></body>
                    </html>
                    """;
            RenderContext ctx = new RenderContext();
            byte[] pdf = service.convertHtml(html.getBytes(), ctx);

            assertThat(pdf).isNotNull();
            assertThat(pdf.length).isGreaterThan(0);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("空 HTML 内容应抛出 FILE_EMPTY 异常")
        @Order(6)
        void shouldRejectEmptyHtml() {
            RenderContext ctx = new RenderContext();
            assertThatThrownBy(() -> service.convertHtml(new byte[0], ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.HTML_TO_PDF_FILE_EMPTY.getCode());
        }

        @Test
        @DisplayName("null HTML 内容应抛出 FILE_EMPTY 异常")
        @Order(7)
        void shouldRejectNullHtml() {
            RenderContext ctx = new RenderContext();
            assertThatThrownBy(() -> service.convertHtml(null, ctx))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.HTML_TO_PDF_FILE_EMPTY.getCode());
        }
    }
}
