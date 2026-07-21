package com.toolbox.controller;

import com.toolbox.model.agent.ChatEvent;
import com.toolbox.service.agent.AgentService;
import com.toolbox.service.agent.ConnectionRegistry;
import com.toolbox.service.agent.ConversationManager;
import com.toolbox.service.agent.EventPublisher;
import com.toolbox.service.store.FileStore;
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

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
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
    private final ConnectionRegistry connectionRegistry;
    private final FileStore fileStore;
    private final ConversationManager conversationManager;
    private final ReActAgent docAgent;
    private final ThreadPoolExecutor toolboxExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final EventPublisher eventPublisher;

    public AgentController(AgentService agentService,
                           ConnectionRegistry connectionRegistry,
                           FileStore fileStore,
                           ConversationManager conversationManager,
                           ReActAgent docAgent,
                           ThreadPoolExecutor toolboxExecutor,
                           ScheduledExecutorService heartbeatExecutor,
                           EventPublisher eventPublisher) {
        this.agentService = agentService;
        this.connectionRegistry = connectionRegistry;
        this.fileStore = fileStore;
        this.conversationManager = conversationManager;
        this.docAgent = docAgent;
        this.toolboxExecutor = toolboxExecutor;
        this.heartbeatExecutor = heartbeatExecutor;
        this.eventPublisher = eventPublisher;

        // SSE 连接断开时自动清理对应对话数据，防止内存泄漏
        this.connectionRegistry.setOnDisconnect(conversationId -> {
            conversationManager.delete(conversationId);
            log.info("[AgentController] cleaned up conversation on disconnect: {}", conversationId);
        });
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

        SseEmitter emitter = new SseEmitter(connectionRegistry.getConnectionTimeoutMs());

        // 新对话: 提前创建 conversation，确保 SSE 断开时能用真实 ID 清理数据
        boolean isNewConversation = (conversationId == null || conversationId.isBlank());
        if (isNewConversation) {
            conversationId = conversationManager.create();
        }

        // 注册连接（并发控制 + 单 conversation 互斥）
        // 心跳 ScheduledFuture，SSE 断开时通过 cleanup 回调取消
        ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat").data("{}"));
            } catch (IOException e) {
                // SSE 已断开，心跳后续会被 cleanup 取消
            }
        }, connectionRegistry.getHeartbeatIntervalMs(),
           connectionRegistry.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);

        if (!connectionRegistry.register(conversationId, emitter,
                () -> heartbeatFuture.cancel(false))) {
            // 注册失败：取消心跳，回滚新创建的对话
            heartbeatFuture.cancel(false);
            if (isNewConversation) {
                conversationManager.delete(conversationId);
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(ChatEvent.error("当前使用人数较多或已有活跃连接，请稍后重试").toJson()));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 异步处理 Agent 对话
        String finalConvId = conversationId;

        toolboxExecutor.execute(() -> {
            connectionRegistry.setProcessing(finalConvId);
            try {
                agentService.handle(message, files, finalConvId, event -> {
                    try {
                        log.info("[AgentController#chat] SSE event: type={}, text={}",
                                event.getType(),
                                event.getText() != null ? event.getText().substring(0,
                                        Math.min(50, event.getText().length())) : "null");
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
                connectionRegistry.clearProcessing(finalConvId);
                connectionRegistry.unregister(finalConvId);
            }
        });


        return emitter;
    }

    /**
     * 取消当前处理 — 仅对活跃中的对话生效
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestParam("conversationId") String conversationId) {
        if (!connectionRegistry.isProcessing(conversationId)) {
            log.info("[AgentController#cancel] conversation {} not active, skip", conversationId);
            return ResponseEntity.ok().build();
        }
        docAgent.interrupt(); // 中断 ReActAgent 推理循环
        connectionRegistry.unregister(conversationId);
        log.info("[AgentController#cancel] cancelled: {}", conversationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 下载处理结果文件
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        try {
            byte[] data = fileStore.load(fileId);
            ByteArrayResource resource = new ByteArrayResource(data);

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
