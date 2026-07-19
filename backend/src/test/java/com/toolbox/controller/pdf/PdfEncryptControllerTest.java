package com.toolbox.controller.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.PdfEncryptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PdfEncryptController 集成测试
 *
 * @author toolbox
 * @since 2026-07-19
 */
@WebMvcTest(PdfEncryptController.class)
@DisplayName("PDF 加密 Controller")
class PdfEncryptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfEncryptService pdfEncryptService;

    // ===== 辅助方法 =====

    private MockMultipartFile createPdfFile(String name) {
        byte[] fakeContent = "%PDF-1.4 fake pdf content".getBytes();
        return new MockMultipartFile("file", name + ".pdf", "application/pdf", fakeContent);
    }

    // ===== 正常场景 =====

    @Test
    @DisplayName("正常加密 — 返回 application/pdf")
    void encryptReturnsPdf() throws Exception {
        byte[] fakeEncrypted = "%PDF-1.4 encrypted".getBytes();
        when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(fakeEncrypted);

        MockMultipartFile file = createPdfFile("report");

        mockMvc.perform(multipart("/api/pdf/encrypt")
                        .file(file)
                        .param("userPassword", "user123")
                        .param("ownerPassword", "owner123")
                        .param("canPrint", "false")
                        .param("canCopy", "true")
                        .param("canModify", "true")
                        .param("canAnnotate", "true")
                        .param("canAssemble", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''report_encrypted.pdf"));
    }

    // ===== 文件校验 =====

    @Nested
    @DisplayName("文件校验")
    class FileValidation {

        @Test
        @DisplayName("空文件 → 400")
        void emptyFile_returns400() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(emptyFile)
                            .param("userPassword", "user123"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("非 PDF 文件 → 400")
        void nonPdfFile_returns400() throws Exception {
            MockMultipartFile txtFile = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(txtFile)
                            .param("userPassword", "user123"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("已加密 PDF → 400")
        void encryptedPdf_returns400() throws Exception {
            when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_ALREADY_ENCRYPTED));

            MockMultipartFile file = createPdfFile("encrypted");

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(file)
                            .param("userPassword", "user123"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== 参数校验 =====

    @Nested
    @DisplayName("参数校验")
    class ParamValidation {

        @Test
        @DisplayName("密码为空 → 400")
        void emptyPassword_returns400() throws Exception {
            when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_EMPTY));

            MockMultipartFile file = createPdfFile("report");

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(file)
                            .param("userPassword", "")
                            .param("ownerPassword", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码相同 → 400")
        void samePasswords_returns400() throws Exception {
            when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_SAME));

            MockMultipartFile file = createPdfFile("report");

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(file)
                            .param("userPassword", "abc123")
                            .param("ownerPassword", "abc123"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码强度不足 → 400")
        void weakPassword_returns400() throws Exception {
            when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK));

            MockMultipartFile file = createPdfFile("report");

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(file)
                            .param("userPassword", "weak"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("权限全开 → 400")
        void allPermissionsOpen_returns400() throws Exception {
            when(pdfEncryptService.encrypt(any(), anyString(), anyString(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PERMISSION_ALL_OPEN));

            MockMultipartFile file = createPdfFile("report");

            mockMvc.perform(multipart("/api/pdf/encrypt")
                            .file(file)
                            .param("userPassword", "user123")
                            .param("ownerPassword", "owner123")
                            .param("canPrint", "true")
                            .param("canCopy", "true")
                            .param("canModify", "true")
                            .param("canAnnotate", "true")
                            .param("canAssemble", "true"))
                    .andExpect(status().isBadRequest());
        }
    }
}
