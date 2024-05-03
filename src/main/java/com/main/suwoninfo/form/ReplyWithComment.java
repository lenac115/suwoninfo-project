package com.main.suwoninfo.form;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyWithComment {

    private Long parentId;
    private Long postId;
    private String content;
}
