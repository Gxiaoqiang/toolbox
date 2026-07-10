package com.toolbox.controller.document;

import com.toolbox.exception.BusinessException;
import com.toolbox.service.document.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    @DisplayName("上传单个合法 docx → 200 + ZIP")
    void singleDocx_returnsZip() throws Exception {
        when(documentService.convertToPdf(any(), eq("test.docx")))
                .thenReturn("%PDF-1.4 fake pdf".getBytes());

        MockMultipartFile file = new MockMultipartFile(
                "files", "test.docx", "application/octet-stream", "dummy".getBytes());

        mockMvc.perform(multipart("/api/document/convert-to-pdf")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    @DisplayName("不支持的扩展名 → 400 DOC_FORMAT_INVALID")
    void invalidExtension_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.txt", "text/plain", "dummy".getBytes());

        mockMvc.perform(multipart("/api/document/convert-to-pdf")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("超过 5 个文件 → 400 DOC_TOO_MANY_FILES")
    void tooManyFiles_returns400() throws Exception {
        var builder = multipart("/api/document/convert-to-pdf");
        for (int i = 1; i <= 6; i++) {
            builder.file(new MockMultipartFile(
                    "files", "doc" + i + ".docx", "application/octet-stream", "dummy".getBytes()));
        }
        mockMvc.perform(builder)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("文件为空 → 400 DOC_FILE_EMPTY")
    void emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "empty.docx", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/api/document/convert-to-pdf")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未上传文件 → 400")
    void missingFiles_returns400() throws Exception {
        mockMvc.perform(multipart("/api/document/convert-to-pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("部分失败时 ZIP 含 _errors.json")
    void partialFailure_zipContainsErrors() throws Exception {
        when(documentService.convertToPdf(any(), eq("good.docx")))
                .thenReturn("%PDF-1.4 fake".getBytes());
        when(documentService.convertToPdf(any(), eq("bad.docx")))
                .thenThrow(new BusinessException(500, "转换失败"));

        MockMultipartFile good = new MockMultipartFile(
                "files", "good.docx", "application/octet-stream", "good".getBytes());
        MockMultipartFile bad = new MockMultipartFile(
                "files", "bad.docx", "application/octet-stream", "bad".getBytes());

        mockMvc.perform(multipart("/api/document/convert-to-pdf")
                        .file(good).file(bad))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }
}
