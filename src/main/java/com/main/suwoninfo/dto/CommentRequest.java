package com.main.suwoninfo.dto;

import java.util.List;

public record CommentRequest(
        String content,
        PostResponse postResponse,
        List<CommentResponse> children,
        CommentResponse parent
) { }