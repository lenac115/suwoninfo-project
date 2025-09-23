package com.main.suwoninfo.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";
    private final RedissonClient client;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.main.suwoninfo.redis.DistributedLock)")
    public Object lock(final ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), pjp.getArgs(), distributedLock.key());
        RLock lock = client.getLock(key);

        try {
            boolean available = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            if (!available) {
                return false;
            }
            return aopForTransaction.proceed(pjp);
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.info("Redisson Lock Already UnLock : serviceName {} key {}", method.getName(), key);
            }
        }
    }
}
