package com.toolbox.controller.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.model.common.PdfArrangeItem;
import com.toolbox.service.pdf.PdfArrangeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PdfArrangeController 集成测试
 *
 * @author toolbox
 * @since 2026-07-18
 */
@WebMvcTest(PdfArrangeController.class)
@DisplayName("PDF 编排 Controller")
class PdfArrangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfArrangeService pdfArrangeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("正常编排返回 PDF")
    void arrangeSuccess() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(pdfArrangeService.arrange(any(), any())).thenReturn(fakePdf);

        String planJson = objectMapper.writeValueAsString(List.of(
                PdfArrangeItem.fromFile(0, 1),
                PdfArrangeItem.fromFile(0, 2)
        ));

        MockMultipartFile file = new MockMultipartFile(
                "files", "test.pdf", "application/pdf", fakePdf);

        mockMvc.perform(multipart("/api/pdf/arrange")
                        .file(file)
                        .param("plan", planJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''arranged.pdf"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("文件为空时返回 400")
    void emptyFileReturns400() throws Exception {
        mockMvc.perform(multipart("/api/pdf/arrange")
                        .param("plan", "[{\"file\":0,\"page\":1}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("plan 为空时返回 400")
    void emptyPlanReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "test.pdf", "application/pdf", "fake".getBytes());

        mockMvc.perform(multipart("/api/pdf/arrange")
                        .file(file)
                        .param("plan", ""))
                .andExpect(status().isBadRequest());
    }
}
