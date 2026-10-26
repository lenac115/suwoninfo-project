package com.main.suwoninfo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * Redis 채널에서 메시지를 받아 "이 서버에 연결된" 구독자에게 전달한다.
 * 서버마다 이 리스너가 하나씩 떠 있으므로 결과적으로 모든 서버의 구독자가 메시지를 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessageResponse chat = objectMapper.readValue(message.getBody(), ChatMessageResponse.class);
            messagingTemplate.convertAndSend(ChatDestinations.room(chat.roomId()), chat);
        } catch (Exception e) {
            // 리스너에서 예외가 새면 해당 메시지만 버려진다. 다음 메시지 처리는 계속됨
            log.error("Redis 채팅 메시지 처리 실패: {}", new String(message.getBody()), e);
        }
    }
}
