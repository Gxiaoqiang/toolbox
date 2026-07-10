package com.toolbox.controller.pdf;

import com.toolbox.service.pdf.PdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PdfController.class)
class PdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfService pdfService;

    private byte[] createMockZip() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("dummy.pdf"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    @Test
    @DisplayName("逐页拆分 — 正常返回 ZIP")
    void byPage_returnsZip() throws Exception {
        when(pdfService.splitPdf(any(), eq("test.pdf"), eq("by-page"), isNull(), eq(0), eq(false)))
                .thenReturn(createMockZip());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "fake-pdf-content".getBytes());

        mockMvc.perform(multipart("/api/pdf/split")
                        .file(file)
                        .param("mode", "by-page"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''pdf-split-result.zip"))
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    @DisplayName("按页码范围 — 正常返回 ZIP")
    void byRange_returnsZip() throws Exception {
        when(pdfService.splitPdf(any(), eq("test.pdf"), eq("by-range"), eq("1,3,5-7"), eq(0), eq(true)))
                .thenReturn(createMockZip());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "fake-pdf-content".getBytes());

        mockMvc.perform(multipart("/api/pdf/split")
                        .file(file)
                        .param("mode", "by-range")
                        .param("pages", "1,3,5-7")
                        .param("preserveMeta", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("每 N 页拆分 — 正常返回 ZIP")
    void byN_returnsZip() throws Exception {
        when(pdfService.splitPdf(any(), eq("test.pdf"), eq("by-n"), isNull(), eq(3), eq(false)))
                .thenReturn(createMockZip());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "fake-pdf-content".getBytes());

        mockMvc.perform(multipart("/api/pdf/split")
                        .file(file)
                        .param("mode", "by-n")
                        .param("everyN", "3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未上传文件 — 返回 400")
    void missingFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/pdf/split")
                        .param("mode", "by-page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("缺少 mode 参数 — 返回 400")
    void missingMode_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "fake-pdf-content".getBytes());

        mockMvc.perform(multipart("/api/pdf/split")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("非 PDF 文件 — 被 FileTypeValidator 拒绝")
    void nonPdfExtension_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/pdf/split")
                        .file(file)
                        .param("mode", "by-page"))
                .andExpect(status().isBadRequest());
    }
}
