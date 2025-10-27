package com.main.suwoninfo.domain;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;

import java.time.Instant;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Time {

    //작성 시간
    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private Instant createdTime;

    //최종 수정 시간
    @LastModifiedDate
    @Column(name = "modified_time", nullable = false)
    private Instant modifiedTime;
}
