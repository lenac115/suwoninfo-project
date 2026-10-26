package com.main.suwoninfo.config;

import com.main.suwoninfo.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 설정
 *
 * 클라이언트 → 서버 : SEND      /pub/chat/message        → @MessageMapping("/chat/message")
 * 서버 → 클라이언트 : SUBSCRIBE /sub/chat/room/{roomId}  ← 서버 메모리 브로커(SimpleBroker)가 전달
 * 에러(본인에게만)  : SUBSCRIBE /user/queue/errors
 *
 * SimpleBroker는 서버 메모리에서만 동작하므로, 서버가 여러 대일 때는
 * Redis Pub/Sub(ChatRedisPublisher/Subscriber)으로 모든 서버에 메시지를 퍼뜨린다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO: 운영 시에는 실제 프론트 도메인으로 제한할 것
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub", "/queue");
        registry.setApplicationDestinationPrefixes("/pub");
        registry.setUserDestinationPrefix("/user");
    }

    // 클라이언트에서 들어오는 모든 STOMP 프레임은 이 인터셉터를 먼저 거친다 (JWT 인증 / 구독 권한 체크)
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
