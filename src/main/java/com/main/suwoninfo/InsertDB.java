package com.main.suwoninfo;

import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import com.main.suwoninfo.dto.PostDto;
import com.main.suwoninfo.form.CommentWithPostId;
import com.main.suwoninfo.form.TokenResponse;
import com.main.suwoninfo.form.UserForm;
import com.main.suwoninfo.form.UserLoginForm;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class InsertDB {

    private final InitService initService;

    @EventListener(WebServerInitializedEvent.class)
    public void init() throws Exception {
        initService.dbInit1();
        initService.dbInit2();
        initService.dbInit3();
    }

    @Component
    @RequiredArgsConstructor
    static class InitService {

        private static final AtomicBoolean RUN_ONCE = new AtomicBoolean(false);
        private final RestClient restClient;

        public void dbInit1() {

            if (!RUN_ONCE.compareAndSet(false, true)) return;
            for (int i = 1; i < 11; i++) {
                System.out.println(i + "번째");
                restClient.post()
                        .uri("http://localhost:8081/users/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(UserForm.builder()
                                .name(String.valueOf(i))
                                .email(i + "@naver.com")
                                .studentNumber(17002038L)
                                .nickname(String.valueOf(i))
                                .password("789456")
                                .build())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(UserForm.class);

            }
        }

        public void dbInit2() throws Exception {

            TokenResponse loginToken = restClient.post()
                    .uri("http://localhost:8081/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(UserLoginForm.builder()
                            .email("1@naver.com")
                            .password("789456")
                            .build())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenResponse.class);

            for (int j = 0; j < 10; j++) {
                for (int i = 1; i<11; i++) {
                    PostDto postDto = PostDto.builder()
                            .content("Free : " + j)
                            .title("자유게시글")
                            .postType(PostType.FREE)
                            .build();
                    //postService.post(Long.valueOf(i), postDto);
                    MultipartBodyBuilder mb = new MultipartBodyBuilder();
                    mb.part("postForm", postDto, MediaType.APPLICATION_JSON);
                    MultiValueMap<String, HttpEntity<?>> parts = mb.build();
                    restClient.post()
                            .uri("http://localhost:8081/post/free/new")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(parts)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .header("Authorization", "Bearer " + loginToken.getAccessToken())
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .toBodilessEntity();
                }
            }

            for (int j = 0; j < 10; j++) {
                for (int i = 1; i<11; i++) {
                    PostDto postDto = PostDto.builder()
                            .content("Trade : " + j)
                            .title("거래게시글")
                            .postType(PostType.TRADE)
                            .tradeStatus(TradeStatus.READY)
                            .price(30000)
                            .build();
                    MultipartBodyBuilder mb = new MultipartBodyBuilder();
                    mb.part("postForm", postDto, MediaType.APPLICATION_JSON);
                    MultiValueMap<String, HttpEntity<?>> parts = mb.build();
                    //postService.post(Long.valueOf(i), postDto);
                    restClient.post()
                            .uri("http://localhost:8081/post/trade/new")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(parts)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .header("Authorization", "Bearer " + loginToken.getAccessToken())
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .toBodilessEntity();
                }
            }
        }

        public void dbInit3() {

            UserLoginForm login = UserLoginForm.builder()
                    .email("1@naver.com")
                    .password("789456")
                    .build();
            TokenResponse loginToken = restClient.post()
                    .uri("http://localhost:8081/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(login)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenResponse.class);
            CommentWithPostId notReply = CommentWithPostId.builder()
                    .content("댓글")
                    .postId(1L)
                    .build();

            restClient.post()
                    .uri("http://localhost:8081/comment/notreply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(notReply)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + loginToken.getAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();

            restClient.post()
                    .uri("http://localhost:8081/comment/notreply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(notReply)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Authorization", "Bearer " + loginToken.getAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            /*commentService.notReplyPost("1@naver.com", 1L, "댓글1");
            commentService.replyPost("1@naver.com", 1L, 1L, "답글1");
            commentService.replyPost("2@naver.com", 1L, 2L, "답글2");
            commentService.replyPost("2@naver.com", 1L, 1L, "답글3");*/
        }
    }
}
