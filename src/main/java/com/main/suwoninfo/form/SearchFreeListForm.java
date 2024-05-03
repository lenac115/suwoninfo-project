package com.main.suwoninfo.form;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFreeListForm {

    private List<PostWithId> postList;
    private boolean activated;
    private int totalPage;
}
