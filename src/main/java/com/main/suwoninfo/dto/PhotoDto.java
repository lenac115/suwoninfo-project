package com.main.suwoninfo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class PhotoDto {

    private Long photoId;

    private String origFileName;

    private String filePath;

    private Long fileSize;
}
