package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostDto {

    private Long postId;
    private String title;
    private String content;
    private String price;
    private PostType postType;
    private TradeStatus tradeStatus;
    private List<PhotoDto> photo;
}
