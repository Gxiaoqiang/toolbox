package com.toolbox.service.agent.skill;

import com.toolbox.service.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 共享上下文 — 管理对话 ID、工具产物、文件加载
 * <p>
 * 各 Skill 通过此对象访问共享状态，避免重复代码。
 *
 * @author toolbox
 * @since 2026-07-22
 */
public class ToolkitContext {

    private static final Logger log = LoggerFactory.getLogger(ToolkitContext.class);

    private final FileStore fileStore;

    /** 当前正在处理的对话 ID — AgentServiceImpl 调用前设置 */
    private volatile String currentConversationId;

    /** 各对话的工具产物 — 按 conversationId 隔离，支持并发 */
    private final ConcurrentHashMap<String, ToolResult> conversationResults = new ConcurrentHashMap<>();

    /** 工具产物记录 */
    public record ToolResult(String fileId, String fileName, long size) {}

    public ToolkitContext(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    /**
     * 设置当前对话 ID — AgentServiceImpl 在调用 agent 前设置
     */
    public void setCurrentConversationId(String conversationId) {
        this.currentConversationId = conversationId;
    }

    /**
     * 获取当前对话 ID
     */
    public String getCurrentConversationId() {
        return currentConversationId;
    }

    /**
     * 存储工具产物
     */
    public void putResult(String conversationId, ToolResult result) {
        conversationResults.put(conversationId, result);
    }

    /**
     * 获取指定对话的工具产物（一次性消费）
     */
    public ToolResult getLastResult(String conversationId) {
        return conversationResults.remove(conversationId);
    }

    /**
     * 清理指定对话的残留产物 — finally 块中调用，防止内存泄漏
     */
    public void clearResult(String conversationId) {
        conversationResults.remove(conversationId);
    }

    /**
     * 加载文件内容
     */
    public byte[] loadFile(String fileId) {
        log.info("[ToolkitContext#loadFile] loading fileId={}", fileId);
        try {
            byte[] data = fileStore.load(fileId);
            log.info("[ToolkitContext#loadFile] loaded {} bytes from {}", data.length, fileId);
            return data;
        } catch (Exception e) {
            log.error("[ToolkitContext#loadFile] file not found: fileId={}", fileId, e);
            throw new RuntimeException("文件不存在或已过期: " + fileId, e);
        }
    }

    /**
     * 存储文件，返回 fileId
     */
    public String storeFile(byte[] data, String fileName) {
        return fileStore.store(data, fileName);
    }

    /**
     * 提取文件基础名（去掉扩展名）
     */
    public static String extractBaseName(String fileId) {
        if (fileId == null) return "file";
        int dot = fileId.lastIndexOf('.');
        return dot > 0 ? fileId.substring(0, dot) : fileId;
    }

    /**
     * 格式化文件大小
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 从 classpath 加载 Prompt 文件
     */
    public static String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            LoggerFactory.getLogger(ToolkitContext.class)
                    .warn("[ToolkitContext#loadPrompt] failed to load {}, using empty", path, e);
            return "";
        }
    }
}
