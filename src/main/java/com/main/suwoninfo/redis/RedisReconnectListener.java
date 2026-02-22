package com.main.suwoninfo.redis;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisReconnectListener {

    private final AtomicBoolean isWarming = new AtomicBoolean(false);
    private final AtomicBoolean isServerReady = new AtomicBoolean(false);

    private final JobLauncher jobLauncher;
    private final Job paginationCacheJob;
    private final PostService postService;

    @EventListener(ApplicationReadyEvent.class)
    public void setIsServerReady() {
        isServerReady.set(true);
    }

    @Async
    @EventListener(RedisConnectedEvent.class)
    public void handleRedisConnected() {

        if(!isServerReady.get()) {
            return;
        }

        if(!isWarming.compareAndSet(false, true)) {
            return;
        }

        try {
            log.info("Redis 연결 복구 감지. 초기 캐시 세팅을 시작합니다.");

            postService.countPost(Post.PostType.TRADE);
            postService.countPost(Post.PostType.FREE);

            JobParameters freeJobParameters = new JobParametersBuilder()
                    .addLong("runTime", System.currentTimeMillis())
                    .addString("post_type", "FREE")
                    .addString("trigger", "WARM_UP") // 스케줄러 실행과 구분하기 위한 이정표
                    .toJobParameters();

            JobParameters tradeJobParameters = new JobParametersBuilder()
                    .addLong("runTime", System.currentTimeMillis())
                    .addString("post_type", "TRADE")
                    .addString("trigger", "WARM_UP") // 스케줄러 실행과 구분하기 위한 이정표
                    .toJobParameters();

            jobLauncher.run(paginationCacheJob, freeJobParameters);
            jobLauncher.run(paginationCacheJob, tradeJobParameters);
        } catch (Exception e) {
            log.error("캐시 초기화 중 오류 발생: ", e);
        } finally {
            isWarming.set(false);
        }
    }
}
