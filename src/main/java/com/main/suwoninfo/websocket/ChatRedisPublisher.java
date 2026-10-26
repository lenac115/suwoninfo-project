package com.main.suwoninfo.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * DB 저장이 끝난 메시지를 Redis 채널로 발행한다.
 *
 * Redis 장애 시 폴백: 최소한 같은 서버에 붙어 있는 구독자에게는 로컬 브로커로 바로 전달한다.
 * 다른 서버의 유저는 실시간으로는 못 받지만, 메시지는 이미 DB에 있으므로
 * 재접속하거나 내역 API를 호출하면 받을 수 있다. (유실 X, 실시간성만 손실)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic chatTopic;
    private final SimpMessageSendingOperations messagingTemplate;

    public void publish(ChatMessageResponse message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("채팅 메시지 직렬화 실패", e);
        }

        try {
            stringRedisTemplate.convertAndSend(chatTopic.getTopic(), payload);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 발행 실패 → 로컬 브로커로만 전달. roomId={}, messageId={}",
                    message.roomId(), message.messageId(), e);
            messagingTemplate.convertAndSend(ChatDestinations.room(message.roomId()), message);
        }
    }
}
