package com.main.suwoninfo.form;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.TradeStatus;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostWithIdAndPrice {

    private Long id;
    private String title;
    private String content;
    private String nickname;
    private TradeStatus tradeStatus;
    private int price;
    private List<Photo> files;
}