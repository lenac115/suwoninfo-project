package com.main.suwoninfo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements ErrorCode {

    NOT_EXIST_TODO(HttpStatus.NOT_FOUND, "시간표 미존재"),
    NOT_EQUAL_USER(HttpStatus.BAD_REQUEST, "게시글 작성자와 다른 유저"),
    DUPLICATED_TODO(HttpStatus.BAD_REQUEST, "시간표 중복");

    private final HttpStatus httpStatus;
    private final String message;
}
