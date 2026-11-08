package com.moimzang.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 도메인에 속하지 않는 공통 응답 코드. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** 성공 응답의 코드 자리. 예외로 던지지 않는다. */
    OK(HttpStatus.OK, "COMMON_200", "요청에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_201", "생성에 성공했습니다."),
    DELETED(HttpStatus.NO_CONTENT, "COMMON_204", "삭제에 성공했습니다."),

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
