package com.moimzang.domain.file.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {

    FILE_001(HttpStatus.CONTENT_TOO_LARGE, "FILE_001", "파일 용량이 허용 범위를 초과했습니다."),
    FILE_002(HttpStatus.BAD_REQUEST, "FILE_002", "허용하지 않는 형식입니다."),
    FILE_003(HttpStatus.BAD_REQUEST, "FILE_003", "일정이 해당 기수에 속하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
