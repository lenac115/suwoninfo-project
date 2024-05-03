package com.main.suwoninfo.form;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchTradeListForm {

    private List<PostWithIdAndPrice> postList;
    private boolean activated;
    private int totalPage;
}