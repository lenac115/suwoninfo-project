package com.main.suwoninfo.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CommentResponse (
        Long id,
        String content,
        List<CommentResponse> children,
        CommentResponse parent,
        UserResponse user,
        PostResponse post
) {}