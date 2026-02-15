package com.main.suwoninfo.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostCursorDto(
        LocalDateTime createdTime,
        Long postId
) {
}
