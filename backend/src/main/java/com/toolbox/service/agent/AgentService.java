package com.toolbox.service.agent;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.web.multipart.MultipartFile;
import com.toolbox.model.agent.ChatEvent;

/**
 * Agent 编排服务接口
 *
 * @author toolbox
 * @since 2026-07-15
 */
public interface AgentService {

    /**
     * 处理用户消息并流式推送事件
     *
     * @param message          用户消息文本
     * @param files            上传的文件（可选）
     * @param conversationId   对话 ID（新对话传 null）
     * @param eventConsumer    事件回调，每产生一个 ChatEvent 就调用一次
     * @return 对话 ID
     */
    String handle(String message, MultipartFile[] files, String conversationId,
                  Consumer<ChatEvent> eventConsumer);
}
