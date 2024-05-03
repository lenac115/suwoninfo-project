package com.main.suwoninfo.form;

import lombok.*;

import java.util.List;

@Builder
@Setter
@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class CommentWithParent {

    private Long id;
    private Long parent;
    private String content;
    private String author;
    private List<CommentWithParent> children;
}
