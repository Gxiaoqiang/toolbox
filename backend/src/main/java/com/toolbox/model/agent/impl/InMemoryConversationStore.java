package com.toolbox.model.agent.impl;

import com.toolbox.model.agent.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现对话存储 — 一期方案，应用重启后数据丢失
 *
 * @author toolbox
 * @since 2026-07-15
 */
public class InMemoryConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    private final ConcurrentHashMap<String, ConversationEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ConversationMessage>> messages = new ConcurrentHashMap<>();
    /** 请求结果缓存 — key = conversationId:fingerprint */
    private final ConcurrentHashMap<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    @Override
    public String create() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        entries.put(id, new ConversationEntry(id, "新对话", 0, now, now));
        messages.put(id, Collections.synchronizedList(new ArrayList<>()));
        log.info("[InMemoryConversationStore#create] conversation created: {}", id);
        return id;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        List<ConversationMessage> msgs = messages.get(conversationId);
        if (msgs == null) return;
        msgs.add(message);

        ConversationEntry entry = entries.get(conversationId);
        if (entry != null) {
            String title = entry.title();
            // 首条用户消息作为对话标题
            if ("新对话".equals(title) && "user".equals(message.role())) {
                title = message.content();
                if (title.length() > 50) title = title.substring(0, 50) + "...";
            }
            entries.put(conversationId, new ConversationEntry(
                conversationId, title, msgs.size(), entry.createdAt(),
                System.currentTimeMillis()));
        }
    }

    @Override
    public List<ConversationMessage> getMessages(String conversationId) {
        List<ConversationMessage> msgs = messages.get(conversationId);
        return msgs != null ? List.copyOf(msgs) : List.of();
    }

    @Override
    public Optional<ConversationEntry> findById(String conversationId) {
        return Optional.ofNullable(entries.get(conversationId));
    }

    @Override
    public void delete(String conversationId) {
        entries.remove(conversationId);
        messages.remove(conversationId);
        // 清理该对话下的所有缓存结果
        resultCache.keySet().removeIf(key -> key.startsWith(conversationId + ":"));
        log.info("[InMemoryConversationStore#delete] conversation deleted: {}", conversationId);
    }

    @Override
    public List<ConversationEntry> listActive() {
        return List.copyOf(entries.values());
    }

    @Override
    public void cacheResult(String conversationId, String fingerprint, CachedResult result) {
        String key = conversationId + ":" + fingerprint;
        resultCache.put(key, result);
        log.info("[InMemoryConversationStore#cacheResult] cached for conv={}, fingerprint={}",
                conversationId, fingerprint);
    }

    @Override
    public Optional<CachedResult> getCachedResult(String conversationId, String fingerprint) {
        String key = conversationId + ":" + fingerprint;
        return Optional.ofNullable(resultCache.get(key));
    }
}
