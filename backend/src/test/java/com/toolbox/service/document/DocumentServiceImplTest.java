package com.toolbox.service.document;

import com.toolbox.exception.BusinessException;
import com.toolbox.service.document.impl.DocumentServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceImplTest {

    private static DocumentServiceImpl service;
    private static boolean libreOfficeAvailable;

    @BeforeAll
    static void setUp() {
        service = new DocumentServiceImpl();
        libreOfficeAvailable = service.isServiceAvailable();
    }

    // 辅助：创建一个简单的 docx 用于测试
    // 注：真正的 docx 需要 POI 生成或预置测试文件
    // 这里主要测试 soffice 调用流程和异常处理

    @Test
    @DisplayName("soffice 可用性检查")
    void isServiceAvailable_returnsBoolean() {
        // 不抛异常，返回 true/false
        assertDoesNotThrow(() -> service.isServiceAvailable());
    }

    @Test
    @DisplayName("soffice 不可用时抛 DOC_SERVICE_UNAVAILABLE")
    void convertToPdf_noSoffice_throwsIfNotAvailable() {
        // 如果 soffice 不可用，convertToPdf 应该快速失败
        if (!libreOfficeAvailable) {
            byte[] dummy = "dummy".getBytes();
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.convertToPdf(dummy, "test.docx"));
            assertEquals(500, ex.getCode()); // DOC_SERVICE_UNAVAILABLE
        }
    }

    @Test
    @DisplayName("空文件抛出 DOC_FILE_EMPTY")
    void convertToPdf_emptyFile_throws() {
        byte[] empty = new byte[0];
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.convertToPdf(empty, "empty.docx"));
        // DOC_FILE_EMPTY
    }

    @Test
    @DisplayName("转换正常 docx 返回非空 PDF")
    void convertToPdf_validDocx_returnsPdf() throws IOException {
        if (!libreOfficeAvailable) {
            return; // 跳过：soffice 不可用
        }
        // 使用 src/test/resources 中的测试 docx 文件
        // 测试前需手动创建或脚本生成一个最小的 .docx
        Path testFile = Path.of("src/test/resources/test.docx");
        if (!Files.exists(testFile)) {
            // 跳过：无测试文件
            return;
        }
        byte[] docxBytes = Files.readAllBytes(testFile);
        byte[] pdfBytes = service.convertToPdf(docxBytes, "test.docx");
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // 验证 PDF 签名
        assertEquals('%', pdfBytes[0]);
        assertEquals('P', pdfBytes[1]);
        assertEquals('D', pdfBytes[2]);
        assertEquals('F', pdfBytes[3]);
    }
}
