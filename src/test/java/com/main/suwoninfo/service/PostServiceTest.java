package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostStatistics;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.repository.*;
import com.main.suwoninfo.utils.RedisUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostQueryRepository postQueryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostStatisticsRepository postStatisticsRepository;

    @Mock
    private PostStatisticsQueryRepository postStatisticsQueryRepository;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private PostService postService;


    @Test
    @DisplayName("countPost: 캐시 히트 시 DB 조회 안 함")
    void countPost_cacheHit() {
        // Given: Redis에 카운트 캐시 존재
        given(redisUtils.versionedKey(anyString(), anyString()))
                .willReturn("v1:posts:FREE:count");
        given(redisUtils.get("v1:posts:FREE:count")).willReturn(500);

        // When
        int count = postService.countPost(Post.PostType.FREE);

        // Then
        assertThat(count).isEqualTo(500);
        verify(postStatisticsQueryRepository, never()).countPost(any());
    }


    @Test
    @DisplayName("countPost: 캐시 미스 시 DB 조회 후 캐시 저장")
    void countPost_cacheMiss() {
        // Given
        given(redisUtils.versionedKey(anyString(), anyString()))
                .willReturn("v1:posts:FREE:count");
        given(redisUtils.get("v1:posts:FREE:count")).willReturn(null);
        given(postStatisticsQueryRepository.countPost(Post.PostType.FREE)).willReturn(1234);

        // When
        int count = postService.countPost(Post.PostType.FREE);

        // Then
        assertThat(count).isEqualTo(1234);
        verify(redisUtils).stringSet(eq("posts:FREE:count"), eq("1234"), any());
    }


    @Test
    @DisplayName("delete: 삭제 시 통계 감소 + Redis zSet에 삭제 ID 기록")
    void delete_updatesStatsAndRedis() {
        // Given
        User user = User.builder().id(1L).email("test@test.com").build();
        Post post = Post.builder().id(10L).title("삭제대상")
                .postType(Post.PostType.FREE).build();

        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));

        PostStatistics stats = PostStatistics.builder()
                .count(100).postType(Post.PostType.FREE).build();
        given(postStatisticsRepository.findByPostType(Post.PostType.FREE)).willReturn(stats);

        // When
        postService.delete(10L, "test@test.com");

        // Then: Redis zSet에 삭제 ID 기록 + DB 삭제
        verify(redisUtils).zSetSet("deleted:post:ids:FREE", 10L);
        verify(postRepository).delete(post);
    }


    @Test
    @DisplayName("findById: 존재하지 않는 게시글 → CustomException")
    void findById_notFound_throwsException() {
        // Given
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(CustomException.class);
    }
}