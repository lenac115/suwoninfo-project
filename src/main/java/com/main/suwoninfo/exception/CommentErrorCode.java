package com.main.suwoninfo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    NOT_EXIST_COMMENT(HttpStatus.NOT_FOUND, "코멘트가 존재하지 않는 경우")
    ;

    private final HttpStatus httpStatus;
    private final String message;

}