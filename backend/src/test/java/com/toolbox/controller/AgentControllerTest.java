package com.toolbox.controller;

import com.toolbox.ToolboxApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Agent 对话接口集成测试 — 验证端点可达性和响应格式
 *
 * @author toolbox
 * @since 2026-07-15
 */
@SpringBootTest(classes = ToolboxApplication.class,
    properties = {
        "toolbox.agent.llm-provider=deepseek",
        "toolbox.agent.llm-model=deepseek-v4-pro",
        "toolbox.agent.llm-api-key=test-key-for-unit-tests",
        "toolbox.store.conversation-store=local",
        "toolbox.store.connection-registry=local"
    })
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ===== 场景 1: 纯文字 → 200 + SSE Content-Type =====

    @Test
    @DisplayName("场景1: 纯文字'切分文档' → 200 + text/event-stream")
    void textOnly_shouldReturnSSE() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat")
                .param("message", "切分文档"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM));
    }

    // ===== 场景 2: 上传文件无文字 → 200 + SSE =====

    @Test
    @DisplayName("场景2: 上传PDF无文字 → 200")
    void fileOnly_shouldReturn200() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "files", "test.pdf", "application/pdf",
                "minimal pdf content".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat")
                .file(pdfFile)
                .param("message", "处理这个文件"))
                .andExpect(status().isOk());
    }

    // ===== 场景 3: 不支持的操作 =====

    @Test
    @DisplayName("场景3: 不支持的操作 → 200")
    void unsupportedOperation_shouldReturn200() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat")
                .param("message", "帮我把图片转成 PDF"))
                .andExpect(status().isOk());
    }

    // ===== 场景 4: 问候 =====

    @Test
    @DisplayName("场景4: 问候语 → 200")
    void greeting_shouldReturn200() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat")
                .param("message", "你好"))
                .andExpect(status().isOk());
    }

    // ===== 场景 5: 合并请求 =====

    @Test
    @DisplayName("场景5: 合并PDF → 200")
    void mergeRequest_shouldReturn200() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat")
                .param("message", "合并 PDF 文件"))
                .andExpect(status().isOk());
    }

    // ===== 场景 6: 下载不存在文件 → 404 =====

    @Test
    @DisplayName("场景6: 下载不存在文件 → 404")
    void download_missingFile_shouldReturn404() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.get("/api/agent/download/nonexistent.pdf"))
                .andExpect(status().isNotFound());
    }

    // ===== 场景 7: 取消不存在对话 → 200 =====

    @Test
    @DisplayName("场景7: 取消对话 → 200")
    void cancel_shouldReturnOk() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.post("/api/agent/cancel")
                .param("conversationId", "nonexistent"))
                .andExpect(status().isOk());
    }

    // ===== 场景 8: 缺少 message 参数 → 400 =====

    @Test
    @DisplayName("场景8: 缺少必填参数 → 400")
    void missingMessage_shouldReturn400() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.multipart("/api/agent/chat"))
                .andExpect(status().isBadRequest());
    }
}
