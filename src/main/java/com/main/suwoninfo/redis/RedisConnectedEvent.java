package com.main.suwoninfo.redis;

import org.springframework.context.ApplicationEvent;

public class RedisConnectedEvent extends ApplicationEvent {
    public RedisConnectedEvent(Object source) {
        super(source);
    }
}
