package com.main.suwoninfo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    NOT_EXIST_CHAT_ROOM(HttpStatus.NOT_FOUND, "채팅방이 존재하지 않는 경우"),
    NOT_CHAT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닌 경우"),
    CANNOT_CHAT_WITH_SELF(HttpStatus.BAD_REQUEST, "자기 자신과 채팅방을 만들려는 경우"),
    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "빈 메시지를 보낸 경우"),
    TOO_LONG_MESSAGE(HttpStatus.BAD_REQUEST, "메시지가 1000자를 넘는 경우")
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
