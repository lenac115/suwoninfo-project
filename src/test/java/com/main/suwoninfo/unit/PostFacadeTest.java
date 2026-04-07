package com.main.suwoninfo.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.service.PostFacade;
import com.main.suwoninfo.service.PostService;
import com.main.suwoninfo.utils.RedisUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PostFacadeTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private RedisUtils redisUtils;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private PostFacade postFacade;


    @Nested
    @DisplayName("findPostList - 캐시 히트/미스 분기")
    class FindPostListTest {

        @Test
        @DisplayName("ID 목록 캐시 히트 + 개별 게시글 캐시 모두 존재 -> full_hit")
        void fullCacheHit() {
            // Given : Redis에 이정표, 카운터, ID 목록, 개별 게시글 캐시 모두 존재
            given(redisUtils.get("new_FREE_posts_count")).willReturn("0");
            given(redisUtils.zSetGetCount(anyString(), any(), any())).willReturn(0L);
        }

    }

}
