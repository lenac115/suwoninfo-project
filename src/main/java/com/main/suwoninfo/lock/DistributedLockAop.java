package com.main.suwoninfo.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
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

    @Around("@annotation(com.main.suwoninfo.lock.DistributedLock)")
    public Object lock(final ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), pjp.getArgs(), distributedLock.key());
        RLock lock = client.getLock(key);

        try {
            boolean available = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            if (!available) {
                throw new CannotAcquireLockException("락을 획득할 수 없습니다. key=" + key);
            }
            log.warn("락 획득 진짜 성공! 트랜잭션 시작. key={}", key);
            return aopForTransaction.proceed(pjp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("락 흭득 중 인터럽트. key=" + key, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.warn("락 해제 성공");
                } catch (Exception e) {
                    log.warn("락 해제 실패. method={} key={} msg={}", method.getName(), key, e.getMessage());
                }
            } else {
                log.debug("락 해제 스킵. method={} key={}", method.getName(), key);
            }
        }
    }
}
