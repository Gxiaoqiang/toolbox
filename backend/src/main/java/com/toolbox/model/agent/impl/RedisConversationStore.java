package com.toolbox.model.agent.impl;

import com.toolbox.model.agent.ConversationStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 对话存储 — ConversationStore 的 Redis 实现
 *
 * <pre>
 * Key 设计:
 *   toolbox:conv:{id}          → Hash  {title, roundCount, createdAt, lastActiveAt}
 *   toolbox:conv:{id}:msgs     → List  [JSON messages]
 *   toolbox:conv:index         → Set   {id1, id2, ...}
 *
 * TTL: conversation TTL（分钟），每次 append 续期
 * </pre>
 *
 * @author toolbox
 * @since 2026-07-17
 */
public class RedisConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

    private static final String KEY_PREFIX = "toolbox:conv:";
    private static final String KEY_INDEX = "toolbox:conv:index";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_ROUND_COUNT = "roundCount";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_ACTIVE_AT = "lastActiveAt";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisConversationStore(StringRedisTemplate redis, int ttlMinutes) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        log.info("[RedisConversationStore] initialized, TTL={}min", ttlMinutes);
    }

    @Override
    public String create() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String key = KEY_PREFIX + id;

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_TITLE, "新对话");
        fields.put(FIELD_ROUND_COUNT, "0");
        fields.put(FIELD_CREATED_AT, String.valueOf(now));
        fields.put(FIELD_LAST_ACTIVE_AT, String.valueOf(now));

        redis.opsForHash().putAll(key, fields);
        redis.expire(key, ttl);
        redis.opsForSet().add(KEY_INDEX, id);
        // 索引 Set 也设 TTL，避免残留已过期会话的 ID
        redis.expire(KEY_INDEX, ttl);

        log.info("[RedisConversationStore#create] conversation created: {}", id);
        return id;
    }

    @Override
    public void append(String conversationId, ConversationMessage message) {
        String key = KEY_PREFIX + conversationId;
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            log.warn("[RedisConversationStore#append] conversation not found: {}",
                    conversationId);
            return;
        }

        // 追加消息
        try {
            String json = objectMapper.writeValueAsString(message);
            redis.opsForList().rightPush(key + ":msgs", json);
        } catch (JsonProcessingException e) {
            log.error("[RedisConversationStore#append] failed to serialize message", e);
            throw new RuntimeException("消息序列化失败", e);
        }

        // 更新元数据 + 续期 TTL
        String title = (String) redis.opsForHash().get(key, FIELD_TITLE);
        if ("新对话".equals(title) && "user".equals(message.role())) {
            title = message.content();
            if (title.length() > 50) title = title.substring(0, 50) + "...";
            redis.opsForHash().put(key, FIELD_TITLE, title);
        }

        long msgCount = redis.opsForList().size(key + ":msgs");
        long now = System.currentTimeMillis();
        redis.opsForHash().put(key, FIELD_ROUND_COUNT, String.valueOf(msgCount));
        redis.opsForHash().put(key, FIELD_LAST_ACTIVE_AT, String.valueOf(now));
        // TTL 续期放 try-finally，避免消息已持久化但未设过期导致内存泄漏
        try {
            redis.expire(key, ttl);
            redis.expire(key + ":msgs", ttl);
            redis.expire(KEY_INDEX, ttl);
        } catch (Exception e) {
            log.warn("[RedisConversationStore#append] TTL renewal failed for {}", conversationId, e);
        }
    }

    @Override
    public List<ConversationMessage> getMessages(String conversationId) {
        String key = KEY_PREFIX + conversationId + ":msgs";
        List<String> jsons = redis.opsForList().range(key, 0, -1);
        if (jsons == null || jsons.isEmpty()) return List.of();

        List<ConversationMessage> result = new ArrayList<>();
        for (String json : jsons) {
            try {
                result.add(objectMapper.readValue(json, ConversationMessage.class));
            } catch (JsonProcessingException e) {
                log.warn("[RedisConversationStore#getMessages] skip corrupt message", e);
            }
        }
        return result;
    }

    @Override
    public Optional<ConversationEntry> findById(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        if (fields.isEmpty()) return Optional.empty();

        try {
            return Optional.of(new ConversationEntry(
                    conversationId,
                    (String) fields.getOrDefault(FIELD_TITLE, ""),
                    Integer.parseInt((String) fields.getOrDefault(FIELD_ROUND_COUNT, "0")),
                    Long.parseLong((String) fields.getOrDefault(FIELD_CREATED_AT, "0")),
                    Long.parseLong((String) fields.getOrDefault(FIELD_LAST_ACTIVE_AT, "0"))
            ));
        } catch (NumberFormatException e) {
            log.warn("[RedisConversationStore#findById] corrupted data for {}, deleting",
                    conversationId, e);
            delete(conversationId);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redis.delete(List.of(key, key + ":msgs"));
        redis.opsForSet().remove(KEY_INDEX, conversationId);
        log.info("[RedisConversationStore#delete] conversation deleted: {}", conversationId);
    }

    @Override
    public List<ConversationEntry> listActive() {
        Set<String> ids = redis.opsForSet().members(KEY_INDEX);
        if (ids == null || ids.isEmpty()) return List.of();

        List<ConversationEntry> result = new ArrayList<>();
        for (String id : ids) {
            findById(id).ifPresent(result::add);
        }
        result.sort((a, b) -> Long.compare(b.lastActiveAt(), a.lastActiveAt()));
        return result;
    }
}
