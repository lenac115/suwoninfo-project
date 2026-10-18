package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.utils.RedisUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PostFacadeTest {

    @Mock
    private PostService postService;

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
        void fullCacheHit() throws InterruptedException {
            // Given : Redis에 이정표, 카운터, ID 목록, 개별 게시글 캐시 모두 존재
            given(redisUtils.get("new_FREE_posts_count")).willReturn("0");
            given(redisUtils.zSetGetCount(anyString(), any(), any())).willReturn(0L);
            given(redisUtils.stringGet("FREE_page:1")).willReturn("10000");

            // ID 목록 캐시 존재
            List<String> cachedIds = List.of("100", "99", "98");
            given(redisUtils.listSet(any(), eq(0), eq(-1))).willReturn(cachedIds);

            // 개별 게시글 캐시, 전부 존재
            PostResponse post1 = PostResponse.builder()
                    .postId(100L)
                    .title("제목1")
                    .postType(Post.PostType.FREE)
                    .build();
            PostResponse post2 = PostResponse.builder()
                    .postId(99L)
                    .title("제목2")
                    .postType(Post.PostType.FREE)
                    .build();
            PostResponse post3 = PostResponse.builder()
                    .postId(98L)
                    .title("제목3")
                    .postType(Post.PostType.FREE)
                    .build();
            given(redisUtils.multiGet(anyList())).willReturn(List.of(post1, post2, post3));

            // When
            List<PostResponse> result = postFacade.findPostList(10, 1, Post.PostType.FREE);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).postId()).isEqualTo(100L);
            // 호출되면 X
            verify(postService, never()).findByPaging(anyInt(), anyInt(), any(), anyInt());
        }

        @Test
        @DisplayName("ID 목록 캐시 미스 -> rebuildFind 호출(DB)")
        void cacheMiss_triggersRebuild() throws InterruptedException {
            // Given : 카운터는 있지만 ID 목록 캐시가 없음
            given(redisUtils.get("new_FREE_posts_count")).willReturn("0");
            given(redisUtils.zSetGetCount(anyString(), any(), any())).willReturn(0L);
            given(redisUtils.stringGet("FREE_page:1")).willReturn("10000");
            // null 반환 -> 캐시 미스
            given(redisUtils.listSet(any(), eq(0), eq(-1))).willReturn(null);

            List<PostResponse> dbResult = List.of(
                    PostResponse.builder()
                            .postId(100L).title("DB에서 온 글").build()
            );
            given(postService.findByPaging(anyInt(), anyInt(), any(), anyInt())).willReturn(dbResult);

            // When
            List<PostResponse> result = postFacade.findPostList(10, 1, Post.PostType.FREE);

            // Then : DB 조회 검증 (호출 됐는지)
            verify(postService).findByPaging(anyInt(), anyInt(), eq(Post.PostType.FREE), anyInt());
        }

        @Test
        @DisplayName("ID 목록은 있지만 개별 게시글 캐시가 하나라도 비면 -> rebuildFind 호출(DB)")
        void partialPostMiss_triggersRebuild() {
            // Given : 게시글 99번이 수정되어 개별 캐시가 무효화된 상태
            given(redisUtils.get("new_FREE_posts_count")).willReturn("0");
            given(redisUtils.zSetGetCount(anyString(), any(), any())).willReturn(0L);
            given(redisUtils.stringGet("FREE_page:1")).willReturn("10000");
            given(redisUtils.listSet(any(), eq(0), eq(-1))).willReturn(List.of("100", "99"));

            PostResponse post1 = PostResponse.builder().postId(100L).title("제목1").build();
            given(redisUtils.multiGet(anyList())).willReturn(java.util.Arrays.asList(post1, null));
            given(postService.findByPaging(anyInt(), anyInt(), any(), anyInt())).willReturn(List.of(post1));

            // When
            postFacade.findPostList(10, 1, Post.PostType.FREE);

            // Then
            verify(postService).findByPaging(anyInt(), anyInt(), eq(Post.PostType.FREE), anyInt());
        }
    }

    @Nested
    @DisplayName("handleRedisDown - 서킷 브레이커 폴백")
    class HandleRedisDownTest {

        @Test
        @DisplayName("page < 10이면 DB 폴백으로 정상 응답")
        void shallowPage_FallbackToDb() {
            // Given
            List<PostResponse> dbResult = List.of(
                    PostResponse.builder()
                            .postId(1L).title("폴백").postType(Post.PostType.FREE).build()
            );
            given(postService.findAbsolutePaging(eq(10), eq(Post.PostType.FREE), anyInt())).willReturn(dbResult);

            // When : page = 5
            List<PostResponse> result = postFacade.handleRedisDown(10, 5, Post.PostType.FREE, new RuntimeException("Redis Down"));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("폴백");
        }

        @Test
        @DisplayName("page > 10이면 TOO_DEEP_PAGE 예외")
        void deepPage_throwsException() {
            // when, then 깊은 페이지는 에러
            assertThatThrownBy(() -> postFacade.handleRedisDown(10, 11, Post.PostType.FREE, new RuntimeException("Redis Down")))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("오프셋 보정 로직")
    class OffsetCorrectionTest {

        @Test
        @DisplayName("새 글 추가 시 오프셋이 음수가 되면 이전 이정표로 폴백")
        void negativeOffset_fallsToPreviousMilestone() throws InterruptedException {
            // Given : 새 글이 많이 추가되어 보정값이 음수
            given(redisUtils.get("new_FREE_posts_count")).willReturn("200");
            given(redisUtils.zSetGetCount(anyString(), any(), any())).willReturn(0L);

            // page=101 -> mileStoneIndex=101
            given(redisUtils.stringGet("FREE_page:101")).willReturn("5000");
            // 음수 -> mileStoneIndex를 1로 내림
            given(redisUtils.stringGet("FREE_page:1")).willReturn("10000");

            given(redisUtils.listSet(any(), eq(0), eq(-1))).willReturn(null);
            given(postService.findByPaging(anyInt(), anyInt(), any(), anyInt()))
                    .willReturn(Collections.emptyList());

            // When
            postFacade.findPostList(10, 101, Post.PostType.FREE);

            // Then : 이전 이정표(page:1)의 커서를 사용했는지 검증
            verify(redisUtils).stringGet("FREE_page:1");
        }
    }
}
