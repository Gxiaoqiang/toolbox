package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.HtmlToPdfService;
import com.toolbox.service.pdf.RenderContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UrlToPdfController 单元测试
 *
 * @author toolbox
 * @since 2026-07-19
 */
@DisplayName("URL 转 PDF 接口")
@WebMvcTest(UrlToPdfController.class)
class UrlToPdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HtmlToPdfService htmlToPdfService;

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake content".getBytes();

    @Nested
    @DisplayName("正常转换")
    class SuccessfulConversion {

        @Test
        @DisplayName("应该返回 PDF 文件流")
        void shouldReturnPdfStream() throws Exception {
            when(htmlToPdfService.convertUrl(eq("https://example.com"), any(RenderContext.class)))
                    .thenReturn(FAKE_PDF);

            mockMvc.perform(post("/api/pdf/url-to-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "url": "https://example.com"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().exists("Content-Disposition"));
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ParameterValidation {

        @Test
        @DisplayName("URL 为空应返回 400")
        void shouldRejectEmptyUrl() throws Exception {
            when(htmlToPdfService.convertUrl(eq(""), any()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY));

            mockMvc.perform(post("/api/pdf/url-to-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "url": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("URL 缺失应返回 400")
        void shouldRejectMissingUrl() throws Exception {
            when(htmlToPdfService.convertUrl(anyString(), any()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY));

            mockMvc.perform(post("/api/pdf/url-to-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("业务异常")
    class BusinessExceptions {

        @Test
        @DisplayName("URL 不可访问应返回对应错误码")
        void shouldReturnUnreachableError() throws Exception {
            when(htmlToPdfService.convertUrl(any(), any()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_UNREACHABLE));

            mockMvc.perform(post("/api/pdf/url-to-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "url": "https://unreachable.example.com"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("渲染超时应返回对应错误码")
        void shouldReturnTimeoutError() throws Exception {
            when(htmlToPdfService.convertUrl(any(), any()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.HTML_TO_PDF_RENDER_TIMEOUT));

            mockMvc.perform(post("/api/pdf/url-to-pdf")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "url": "https://slow.example.com"
                                    }
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }
}
