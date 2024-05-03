package com.main.suwoninfo.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PhotoErrorCode implements ErrorCode {

    NOT_EXIST_PHOTO(HttpStatus.NOT_FOUND, "사진이 존재하지 않는 경우"),
    OVER_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "사진 갯수 혹은 용량 초과")
    ;

    private final HttpStatus httpStatus;
    private final String message;

}