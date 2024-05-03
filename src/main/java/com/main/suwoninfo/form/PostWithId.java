package com.main.suwoninfo.form;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.TradeStatus;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostWithId {

    private Long id;
    private String nickname;
    private String title;
    private String content;
    private List<Photo> files;
}