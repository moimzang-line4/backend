package com.moimzang.domain.chat.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "CHAT_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    CHAT_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    CHAT_404_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_404_NOT_FOUND", "존재하지 않는 대화방입니다."),
    CHAT_502_AI_FAILED(HttpStatus.BAD_GATEWAY, "CHAT_502_AI_FAILED", "처리하지 못했어요. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
