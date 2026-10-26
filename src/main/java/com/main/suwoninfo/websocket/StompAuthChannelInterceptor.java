package com.main.suwoninfo.websocket;

import com.main.suwoninfo.jwt.JwtTokenProvider;
import com.main.suwoninfo.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

/**
 * STOMP 프레임 단위 인증/인가
 *
 * 브라우저 WebSocket API는 핸드셰이크 요청에 Authorization 헤더를 붙일 수 없어서
 * HTTP 필터(JwtFilter)가 아니라 STOMP CONNECT 프레임의 헤더로 JWT를 받는다.
 * CONNECT 때 넣어둔 Principal은 세션에 저장되어 이후 SEND/SUBSCRIBE 프레임에 자동으로 붙는다.
 *
 * 여기서 예외를 던지면 클라이언트에 STOMP ERROR 프레임이 가고 연결이 끊긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/room/";
    private static final String ERROR_QUEUE_DESTINATION = "/user/queue/errors";

    private final JwtTokenProvider tokenProvider;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // getAccessor로 꺼내야 수정 가능한(mutable) accessor가 나온다. setUser가 반영되려면 필수
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> requireUser(accessor);
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Authorization 헤더가 없습니다.");
        }

        String token = header.substring(BEARER_PREFIX.length());
        if (!Boolean.TRUE.equals(tokenProvider.validateToken(token).getValid())) {
            // 만료 포함. WebSocket에는 HTTP처럼 재발급 흐름을 끼워 넣을 수 없으니
            // 클라이언트가 /users/reissue로 재발급 받은 뒤 다시 CONNECT 하도록 한다.
            throw new AccessDeniedException("유효하지 않거나 만료된 토큰입니다.");
        }

        Authentication authentication = tokenProvider.getAuthentication(token);
        accessor.setUser(authentication);
        log.debug("STOMP CONNECT: {}", authentication.getName());
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();

        if (ERROR_QUEUE_DESTINATION.equals(destination)) {
            return;
        }

        // 허용된 목적지만 구독 가능. SimpleBroker는 "/sub/chat/room/*" 같은 패턴 구독도 받아주기 때문에
        // roomId를 숫자로 엄격하게 파싱해서 와일드카드로 남의 방을 엿듣는 것을 막는다.
        if (destination == null || !destination.startsWith(ROOM_DESTINATION_PREFIX)) {
            throw new AccessDeniedException("구독할 수 없는 경로입니다: " + destination);
        }

        Long roomId = parseRoomId(destination.substring(ROOM_DESTINATION_PREFIX.length()));
        if (!chatService.isMember(user.getName(), roomId)) {
            throw new AccessDeniedException("채팅방 참여자가 아닙니다. roomId=" + roomId);
        }
    }

    private Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("인증되지 않은 STOMP 세션입니다.");
        }
        return user;
    }

    private Long parseRoomId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("잘못된 채팅방 경로입니다.");
        }
    }
}
