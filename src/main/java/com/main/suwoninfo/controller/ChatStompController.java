package com.main.suwoninfo.controller;

import com.main.suwoninfo.dto.ChatMessageRequest;
import com.main.suwoninfo.dto.ChatMessageResponse;
import com.main.suwoninfo.dto.ErrorResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.service.ChatService;
import com.main.suwoninfo.websocket.ChatRedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 핸들러
 * 클라이언트가 SEND /pub/chat/message 하면 여기로 들어온다 (/pub 은 WebSocketConfig의 prefix)
 *
 * 흐름: 저장(DB, 트랜잭션 커밋) → Redis publish → 모든 서버의 Subscriber → 각 서버 구독자에게 전달
 * 저장이 실패하면 발행하지 않으므로 "화면엔 보였는데 DB엔 없는" 메시지가 생기지 않는다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final ChatRedisPublisher chatRedisPublisher;

    @MessageMapping("/chat/message")
    public void sendMessage(@Payload ChatMessageRequest request, Principal principal) {
        // 보낸 사람은 payload가 아니라 CONNECT 때 JWT로 인증된 Principal(email)에서 꺼낸다
        ChatMessageResponse saved = chatService.saveMessage(principal.getName(), request.roomId(), request.content());
        chatRedisPublisher.publish(saved);
    }

    // @MessageMapping에서 난 예외는 GlobalExceptionHandler(@RestControllerAdvice)가 잡지 못한다.
    // 보낸 사람 본인에게만 /user/queue/errors 로 돌려준다.
    @MessageExceptionHandler(CustomException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleCustomException(CustomException e) {
        return ErrorResponse.builder()
                .code(e.getErrorCode().name())
                .message(e.getErrorCode().getMessage())
                .build();
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleException(Exception e) {
        log.error("STOMP 메시지 처리 중 오류", e);
        return ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("메시지 전송에 실패했습니다.")
                .build();
    }
}
