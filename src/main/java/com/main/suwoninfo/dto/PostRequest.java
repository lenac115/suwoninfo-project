package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.Post;
import lombok.Builder;

import java.util.List;

@Builder
public record PostRequest(
        String title,
        String content,
        Integer price,
        Post.PostType postType,
        Post.TradeStatus tradeStatus,
        List<PhotoResponse> photos
) { }
