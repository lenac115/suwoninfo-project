package com.main.suwoninfo;

import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import com.main.suwoninfo.dto.PostDto;
import com.main.suwoninfo.dto.UserDto;
import com.main.suwoninfo.service.CommentService;
import com.main.suwoninfo.service.PostService;
import com.main.suwoninfo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class InsertDB {

    private final InitService initService;

    @PostConstruct
    public void init() throws Exception {
        initService.dbInit1();
        initService.dbInit2();
        initService.dbInit3();
    }

    @Component
    @Transactional
    @RequiredArgsConstructor
    static class InitService {

        private final UserService userService;
        private final PostService postService;
        private final CommentService commentService;

        public void dbInit1() {
            UserDto userDto = UserDto.builder()
                    .name("1")
                    .email("1234@naver.com")
                    .studentNumber(17002038L)
                    .nickname("11")
                    .password("456789")
                    .build();
            UserDto userDto2 = UserDto.builder()
                    .name("2")
                    .email("4567@naver.com")
                    .studentNumber(17002038L)
                    .nickname("22")
                    .password("789456")
                    .build();
            userService.join(userDto);
            userService.join(userDto2);
        }

        public void dbInit2() throws Exception {

            for(int j = 0; j<11; j++) {
                PostDto postDto = PostDto.builder()
                        .content("Free : " + Integer.toString(j))
                        .title("자유게시글")
                        .postType(PostType.FREE)
                        .build();
                postService.post(1L, postDto);
            }

            for(int j = 0; j<11; j++) {
                PostDto postDto = PostDto.builder()
                        .content("Trade : " + Integer.toString(j))
                        .title("거래게시글")
                        .postType(PostType.TRADE)
                        .tradeStatus(TradeStatus.READY)
                        .price("30000")
                        .build();
                postService.post(1L, postDto);
            }
        }

        public void dbInit3() {

            commentService.notReplyPost("1234@naver.com", 1L, "댓글1");
            commentService.replyPost("1234@naver.com", 1L, 1L, "답글1");
            commentService.replyPost("4567@naver.com", 1L, 2L, "답글2");
            commentService.replyPost("4567@naver.com", 1L, 1L, "답글3");
        }
    }
}
