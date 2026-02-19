package com.main.suwoninfo.batch;

import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationCacheScheduler {

    private final JobLauncher jobLauncher;
    private final Job job;
    private final RedisUtils redisUtils;


    @Scheduled(cron = "0 0 */4 * * *")
    public void runPaginationCacheScheduler() {

        String[] postTypes = {"FREE", "TRADE"};

        for(String postType : postTypes) {
            try {
                log.info("페이징 커서 배치 시작");

                JobParameters jobParameters = new JobParametersBuilder()
                        .addString("post_type", postType)
                        .addLong("runTime", System.currentTimeMillis())
                        .toJobParameters();

                jobLauncher.run(job, jobParameters);

                log.info("페이징 커서 배치 성공");
                redisUtils.stringSet("new_FREE_posts_count", "0");
                redisUtils.stringSet("new_TRADE_posts_count", "0");
                redisUtils.zSetDelete("deleted:post:ids:" + postType);
            } catch (Exception e) {
                log.error("페이징 커서 배치 실패 : {}", e.getMessage());
            }
        }
    }
}
