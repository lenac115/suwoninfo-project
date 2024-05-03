package com.main.suwoninfo.form;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentListForm {

    private List<CommentWithParent> commentList;
    private int totalPage = 1;
}
