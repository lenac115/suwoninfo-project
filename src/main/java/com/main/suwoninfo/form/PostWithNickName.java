package com.main.suwoninfo.form;

import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import com.main.suwoninfo.dto.PhotoDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostWithNickName {

    private Long postId;
    private String title;
    private String nickname;
    private String content;
    private String price;
    private PostType postType;
    private TradeStatus tradeStatus;
    private List<PhotoDto> photo;
}