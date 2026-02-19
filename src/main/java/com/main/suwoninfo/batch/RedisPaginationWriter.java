package com.main.suwoninfo.batch;

import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@StepScope
@Slf4j
public class RedisPaginationWriter implements ItemWriter<Long> {

    private final RedisUtils redisUtils;

    @Value("#{jobParameters['post_type']}")
    private String type;

    @Value("#{stepExecution}")
    private StepExecution stepExecution;

    private final int ITEMS_PER_PAGE = 10; // 몇 페이지 단위로 청크의 이정표를 세울지

    @Override
    public void write(Chunk<? extends  Long> chunk) {
        if(chunk.isEmpty()) return;

        Long startCursorId = chunk.getItems().get(0);
        long currentReadCount = stepExecution.getReadCount();
        int startPageNum = (int) (currentReadCount / ITEMS_PER_PAGE) + 1;

        String redisKey = type + "_page:" + startPageNum;

        log.info("[{}] Saving Redis - Page: {}, CursorID: {}", type, startPageNum, startCursorId);
        redisUtils.stringSet(redisKey, String.valueOf(startCursorId), Duration.ofMinutes(1440));
    }
}
