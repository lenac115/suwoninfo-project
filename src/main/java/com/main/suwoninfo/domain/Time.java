package com.main.suwoninfo.domain;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Time {

    //작성 시간
    @CreatedDate
    private LocalDateTime createdTime;

    //최종 수정 시간
    @LastModifiedDate
    private LocalDateTime modifiedTime;
}
