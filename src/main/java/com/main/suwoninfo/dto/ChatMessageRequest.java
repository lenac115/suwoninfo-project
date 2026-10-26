package com.main.suwoninfo.dto;

/**
 * 클라이언트가 STOMP SEND /pub/chat/message 로 보내는 payload
 * 보낸 사람은 payload가 아니라 CONNECT 때 인증된 Principal에서 꺼낸다 (위조 방지)
 */
public record ChatMessageRequest(
        Long roomId,
        String content
) {
}
