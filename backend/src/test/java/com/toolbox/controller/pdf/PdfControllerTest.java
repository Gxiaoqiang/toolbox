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

    private byte[] createMockPdfContent() {
        // PDF 文件必须以 %PDF 开头，否则魔数校验会拒绝
        return "%PDF-1.4\nfake pdf content\n%%EOF\n".getBytes();
    }

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
                "file", "test.pdf", "application/pdf", createMockPdfContent());

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
                "file", "test.pdf", "application/pdf", createMockPdfContent());

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
                "file", "test.pdf", "application/pdf", createMockPdfContent());

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
                "file", "test.pdf", "application/pdf", createMockPdfContent());

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

    // ========== PDF 合并测试 ==========

    @Test
    @DisplayName("合并 — 正常返回 PDF")
    void merge_returnsPdf() throws Exception {
        when(pdfService.mergePdf(anyList(), eq(false)))
                .thenReturn(new byte[]{1, 2, 3});

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.pdf", "application/pdf", createMockPdfContent());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.pdf", "application/pdf", createMockPdfContent());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''merged.pdf"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("合并 — preserveMeta=true 正常")
    void merge_preserveMeta_returnsPdf() throws Exception {
        when(pdfService.mergePdf(anyList(), eq(true)))
                .thenReturn(new byte[]{1, 2, 3});

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.pdf", "application/pdf", createMockPdfContent());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.pdf", "application/pdf", createMockPdfContent());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(file1)
                        .file(file2)
                        .param("preserveMeta", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("合并 — 仅 1 个文件返回错误")
    void merge_singleFile_returnsError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.pdf", "application/pdf", createMockPdfContent());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("合并 — 非 PDF 文件返回错误")
    void merge_nonPdf_returnsError() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.txt", "text/plain", "not-pdf".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.pdf", "application/pdf", createMockPdfContent());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("合并 — 空文件返回错误")
    void merge_emptyFile_returnsError() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.pdf", "application/pdf", new byte[0]);
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.pdf", "application/pdf", createMockPdfContent());

        mockMvc.perform(multipart("/api/pdf/merge")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isBadRequest());
    }
}
