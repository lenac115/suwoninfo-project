package com.main.suwoninfo.form;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostListForm {

    private List<PostWithId> postList;
    private int totalPage;
}
