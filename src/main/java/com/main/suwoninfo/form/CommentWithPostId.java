package com.main.suwoninfo.form;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentWithPostId {

    private Long postId;
    private String content;
}
