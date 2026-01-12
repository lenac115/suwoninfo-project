package com.main.suwoninfo.form;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostListForm {

    private List<PostWithIdAndPrice> postList;
    private int totalPage;
}
