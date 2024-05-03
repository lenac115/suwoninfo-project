package com.main.suwoninfo.form;

import com.main.suwoninfo.domain.Photo;
import lombok.Getter;

@Getter
public class PhotoResponse {

    private Long fileId;  // 파일 id

    public PhotoResponse(Photo entity){
        this.fileId = entity.getId();
    }
}
