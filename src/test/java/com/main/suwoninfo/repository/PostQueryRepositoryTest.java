package com.main.suwoninfo.repository;

import com.main.suwoninfo.config.AppConfig;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PostQueryRepository.class, AppConfig.class})// QueryDSL 빈 수동 등록
class PostQueryRepositoryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private PostQueryRepository postQueryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@test.com")
                .password("1234")
                .name("testname")
                .nickname("testnick")
                .studentNumber(1130000L)
                .auth(User.Auth.USER)
                .activated(true)
                .build();
        em.persist(testUser);

        for (int i = 1; i <= 20; i++) {
            Post post = Post.builder()
                    .title("게시글" + i)
                    .content("내용" + i)
                    .postType(i <= 15 ? Post.PostType.FREE : Post.PostType.TRADE)
                    .users(testUser)
                    .price(0)
                    .build();
            em.persist(post);
        }
        em.flush();
        em.clear();
    }


    @Test
    @DisplayName("findByCursorPaging : 커서 ID 이하 + 최신순 + limit")
    void cursorPaging_returnsCorrectRange() {
        // When : mileStoneOffset=15, limit=5, pagingOffset=0
        List<Post> result = postQueryRepository.findByCursorPaging(
                5, 15, Post.PostType.FREE, 0
        );

        // Then: 5개, ID 내림차순, FREE 타입만
        assertThat(result).hasSize(5);
        assertThat(result.get(0).getId()).isGreaterThan(result.get(4).getId());
        assertThat(result).allMatch(p -> p.getPostType() == Post.PostType.FREE);
    }


    @Test
    @DisplayName("findByCursorPaging: pagingOffset 적용")
    void cursorPaging_withOffset() {
        // When: pagingOffset=5 → 5개 건너뛴 후 조회
        List<Post> result = postQueryRepository.findByCursorPaging(
                5, 15, Post.PostType.FREE, 5
        );

        // Then: 앞의 5개를 건너뛴 결과
        assertThat(result).hasSize(5);
    }


    @Test
    @DisplayName("findByTitle: 키워드 검색 + 타입 필터")
    void searchByTitle() {
        List<Post> result = postQueryRepository.findByTitle(
                "게시글1", 20, 0, Post.PostType.FREE
        );

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(p -> p.getTitle().contains("게시글1"));
        assertThat(result).allMatch(p -> p.getPostType() == Post.PostType.FREE);
    }


    @Test
    @DisplayName("findAbsolutePaging: Redis 장애 폴백용 절대 오프셋 조회")
    void absolutePaging() {
        List<Post> result = postQueryRepository.findAbsolutePaging(
                5, Post.PostType.FREE, 10
        );

        assertThat(result).hasSize(5);
        assertThat(result.get(0).getId()).isGreaterThan(result.get(4).getId());
    }
}