package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.ChatMessage;
import lombok.Builder;

/**
 * 구독자에게 전달되고 Redis 채널로도 오가는 메시지 형태
 * sentAt은 Instant 대신 ISO-8601 문자열로 둔다.
 * (프로젝트의 ObjectMapper 빈에 JavaTimeModule이 등록돼 있지 않아 Instant 직렬화가 실패하기 때문)
 */
@Builder
public record ChatMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderNickname,
        String content,
        String sentAt
) {
    public static ChatMessageResponse from(ChatMessage m) {
        return ChatMessageResponse.builder()
                .messageId(m.getId())
                .roomId(m.getChatRoom().getId())
                .senderId(m.getSender().getId())
                .senderNickname(m.getSender().getNickname())
                .content(m.getContent())
                .sentAt(m.getCreatedTime() == null ? null : m.getCreatedTime().toString())
                .build();
    }
}
