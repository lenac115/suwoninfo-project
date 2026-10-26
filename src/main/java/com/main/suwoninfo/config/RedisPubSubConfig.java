package com.main.suwoninfo.config;

import com.main.suwoninfo.websocket.ChatRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 채팅용 Redis Pub/Sub 설정
 *
 * 모든 서버 인스턴스가 같은 채널(suwoninfo:chat)을 구독한다.
 * 어느 서버에서 publish 하든 모든 서버의 ChatRedisSubscriber가 메시지를 받고,
 * 각자 자기 메모리 브로커에 붙어 있는 구독자에게 전달한다.
 */
@Configuration
public class RedisPubSubConfig {

    public static final String CHAT_CHANNEL = "suwoninfo:chat";

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                        ChatRedisSubscriber chatRedisSubscriber,
                                                                        ChannelTopic chatTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatRedisSubscriber, chatTopic);
        // Redis 연결이 끊기면 5초마다 재구독 시도 (기본값과 같지만 의도를 명시)
        container.setRecoveryInterval(5000L);
        return container;
    }
}
