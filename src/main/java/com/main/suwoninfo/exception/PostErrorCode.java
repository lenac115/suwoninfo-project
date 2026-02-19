package com.main.suwoninfo.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements ErrorCode {

    NOT_EXIST_TITLE(HttpStatus.NOT_FOUND, "제목이 같은 게시글이 존재하지 않는 경우"),
    NOT_EQUAL_USER(HttpStatus.BAD_REQUEST, "게시글 작성자와 다른 유저"),
    NOT_EXIST_POST(HttpStatus.NOT_FOUND, "포스트가 존재하지 않는 경우"),
    POST_TYPE_ERROR(HttpStatus.BAD_REQUEST, "포스트의 타입이 없는 경우"),
    TOO_DEEP_PAGE(HttpStatus.BAD_REQUEST, "Redis 다운 중에 너무 깊은 페이지를 조회하려는 경우");

    private final HttpStatus httpStatus;
    private final String message;

}