package com.toolbox.controller;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.service.agent.AgentService;
import com.toolbox.service.agent.ConversationManager;
import com.toolbox.service.agent.FileManager;
import com.toolbox.service.agent.SseConnectionManager;
import io.agentscope.core.ReActAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 对话接口 — SSE 流式返回，支持文件上传
 *
 * @author toolbox
 * @since 2026-07-15
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final SseConnectionManager sseConnectionManager;
    private final FileManager fileManager;
    private final ConversationManager conversationManager;
    private final ReActAgent docAgent;

    public AgentController(AgentService agentService,
                           SseConnectionManager sseConnectionManager,
                           FileManager fileManager,
                           ConversationManager conversationManager,
                           ReActAgent docAgent) {
        this.agentService = agentService;
        this.sseConnectionManager = sseConnectionManager;
        this.fileManager = fileManager;
        this.conversationManager = conversationManager;
        this.docAgent = docAgent;
    }

    /**
     * Agent 对话 — SSE 流式返回
     *
     * @param message        用户消息（必填）
     * @param files          上传文件（可选）
     * @param conversationId 对话 ID（可选，新对话不传）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam("message") String message,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "conversationId", required = false) String conversationId) {

        SseEmitter emitter = new SseEmitter(sseConnectionManager.getConnectionTimeoutMs());

        // 注册连接（并发控制 + 单 conversation 互斥）
        String effectiveConvId = conversationId != null ? conversationId
                : "new-" + System.currentTimeMillis();
        if (!sseConnectionManager.register(effectiveConvId, emitter)) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(ChatEvent.error("当前使用人数较多或已有活跃连接，请稍后重试").toJson()));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 启动心跳定时器
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat").data("{}"));
            } catch (IOException e) {
                heartbeat.shutdown();
            }
        }, sseConnectionManager.getHeartbeatIntervalMs(),
           sseConnectionManager.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

        // 异步处理 Agent 对话
        String finalConvId = conversationId;
        new Thread(() -> {
            try {
                agentService.handle(message, files, finalConvId, event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(event.getType())
                                .data(event.toJson()));
                    } catch (IOException e) {
                        log.warn("[AgentController#chat] SSE send failed", e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("[AgentController#chat] agent processing failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(ChatEvent.error("处理时遇到了问题，请稍后重试。").toJson()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                heartbeat.shutdown();
                sseConnectionManager.unregister(effectiveConvId);
            }
        }, "agent-chat-" + effectiveConvId).start();

        return emitter;
    }

    /**
     * 取消当前处理
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestParam("conversationId") String conversationId) {
        docAgent.interrupt(); // 中断 ReActAgent 推理循环
        sseConnectionManager.unregister(conversationId);
        log.info("[AgentController#cancel] cancelled: {}", conversationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 下载处理结果文件
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        try {
            File file = fileManager.load(fileId);
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(file.toPath()));

            String filename = fileId.contains(".") ? fileId : fileId + ".bin";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.warn("[AgentController#download] file not found: {}", fileId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable String id) {
        conversationManager.delete(id);
        return ResponseEntity.ok().build();
    }
}
