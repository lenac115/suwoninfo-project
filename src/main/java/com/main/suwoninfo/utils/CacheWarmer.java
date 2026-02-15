package com.main.suwoninfo.utils;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.service.PostFacade;
import com.main.suwoninfo.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer {

    private final PostService postService;

    private final JobLauncher jobLauncher;
    private final Job paginationCacheJob;

    @EventListener(ApplicationReadyEvent.class) // 서버 뜰 때 실행
    public void warmUp() {
        log.info("[Warm-up] 실제 데이터 로딩 시작...");

        try {
            postService.countPost(Post.PostType.FREE);
            postService.countPost(Post.PostType.TRADE);

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

            // 배치 수동 실행
            jobLauncher.run(paginationCacheJob, freeJobParameters);
            jobLauncher.run(paginationCacheJob, tradeJobParameters);


            log.info("[Warm-up] 끝");
        } catch (Exception e) {
            log.error("웜업 실패", e);
        }
    }
}