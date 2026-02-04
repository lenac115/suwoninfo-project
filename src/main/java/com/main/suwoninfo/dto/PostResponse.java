package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.Post;
import lombok.*;

import java.util.List;

@Builder
public record PostResponse(
        Long postId,
        String title,
        String content,
        Integer price,
        Post.PostType postType,
        Post.TradeStatus tradeStatus,
        List<PhotoResponse> photos
) {}