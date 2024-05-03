package com.main.suwoninfo.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NOT_EXIST_EMAIL(HttpStatus.NOT_FOUND, "이메일이 존재하지 않는 경우"),
    NOT_EQUAL_EMAIL(HttpStatus.FORBIDDEN, "아이디가 다른 경우"),
    NOT_CORRECT_PASSWORD(HttpStatus.FORBIDDEN, "비밀번호가 틀린 경우"),
    NOT_ACTIVATED_ACCOUNT(HttpStatus.UNAUTHORIZED, "계정이 비활성화 된 경우"),
    VALIDATED_EMAIL_ERROR(HttpStatus.BAD_REQUEST, "회원가입 시 중복된 이메일"),
    VALIDATED_NICK_ERROR(HttpStatus.BAD_REQUEST, "회원가입 시 중복된 별명"),
    LOGOUT_USER_ERROR(HttpStatus.BAD_REQUEST, "로그아웃된 상태일 경우"),
    INVALID_USER_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 토큰일 경우"),
    NOT_AVAILABLE_EMAIL(HttpStatus.FORBIDDEN, "허용되지 않는 이메일")
    ;

    private final HttpStatus httpStatus;
    private final String message;

}