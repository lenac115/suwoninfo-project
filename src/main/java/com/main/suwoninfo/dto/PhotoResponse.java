package com.main.suwoninfo.dto;

import lombok.Builder;

@Builder
public record PhotoResponse(
        Long photoId,
        String origFileName,
        String filePath,
        Long fileSize
) { }
