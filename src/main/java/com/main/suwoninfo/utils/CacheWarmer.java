package com.main.suwoninfo.utils;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.service.PostFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer {

    private final PostFacade postFacade;

    @EventListener(ApplicationReadyEvent.class) // 서버 뜰 때 실행
    public void warmUp() {
        log.info("[Warm-up] 실제 데이터 로딩 시작...");

        try {
            postFacade.findPostList(10, 0, Post.PostType.TRADE);
            postFacade.findPostList(10, 10, Post.PostType.TRADE);
            postFacade.findPostList(10, 20, Post.PostType.TRADE);
            postFacade.findPostList(10, 30, Post.PostType.TRADE);


            log.info("[Warm-up] 끝");
        } catch (Exception e) {
            log.error("웜업 실패", e);
        }
    }
}