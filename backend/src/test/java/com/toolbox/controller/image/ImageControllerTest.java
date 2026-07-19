package com.toolbox.controller.image;

import com.toolbox.service.image.ImageToPdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ImageController 集成测试
 *
 * @author toolbox
 * @since 2026-07-19
 */
@WebMvcTest(ImageController.class)
@DisplayName("图片转 PDF Controller")
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImageToPdfService imageToPdfService;

    // ===== 辅助方法 =====

    private MockMultipartFile createImageFile(String name, String ext) {
        byte[] fakeContent = "fake-image-content".getBytes();
        return new MockMultipartFile("files", name + "." + ext,
                "image/" + ext, fakeContent);
    }

    // ===== 正常场景 =====

    @Test
    @DisplayName("单张图片转 PDF — 返回 application/pdf")
    void singleImageReturnsPdf() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(imageToPdfService.convertToPdf(anyList(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(fakePdf);

        MockMultipartFile file = createImageFile("photo", "jpg");

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''images.pdf"));
    }

    @Test
    @DisplayName("merge=false 返回 application/zip")
    void mergeFalseReturnsZip() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(imageToPdfService.convertToPdf(anyList(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(fakePdf);

        MockMultipartFile file = createImageFile("photo", "png");

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file)
                        .param("merge", "false"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''images.zip"));
    }

    @Test
    @DisplayName("自定义参数传递 — orientation/fitMode/margin")
    void customParams() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(imageToPdfService.convertToPdf(anyList(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(fakePdf);

        MockMultipartFile file = createImageFile("photo", "jpg");

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file)
                        .param("orientation", "landscape")
                        .param("margin", "large")
                        .param("fitMode", "cover"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    // ===== 校验场景 =====

    @Test
    @DisplayName("无文件时返回 400")
    void noFileReturns400() throws Exception {
        mockMvc.perform(multipart("/api/image/to-pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("不支持的格式返回 400")
    void unsupportedFormatReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "document.pdf", "application/pdf", "fake".getBytes());

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BMP 格式返回 400")
    void bmpFormatReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "image.bmp", "image/bmp", "fake".getBytes());

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("超过 50 张图片返回 400")
    void tooManyFilesReturns400() throws Exception {
        var requestBuilder = multipart("/api/image/to-pdf");
        for (int i = 0; i < 51; i++) {
            requestBuilder.file(createImageFile("img" + i, "jpg"));
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("单文件超大返回 400")
    void singleFileTooLarge() throws Exception {
        // 创建超过 5MB 的文件
        byte[] oversized = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "files", "huge.jpg", "image/jpeg", oversized);

        mockMvc.perform(multipart("/api/image/to-pdf")
                        .file(file))
                .andExpect(status().isBadRequest());
    }
}
