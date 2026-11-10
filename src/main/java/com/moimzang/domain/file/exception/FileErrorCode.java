package com.moimzang.domain.file.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {

    FILE_413_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "FILE_413_SIZE_EXCEEDED", "파일 용량이 허용 범위를 초과했습니다."),
    FILE_400_EXTENSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FILE_400_EXTENSION_NOT_ALLOWED", "허용하지 않는 형식입니다."),
    FILE_400_SCHEDULE_TERM_MISMATCH(HttpStatus.BAD_REQUEST, "FILE_400_SCHEDULE_TERM_MISMATCH", "일정이 해당 기수에 속하지 않습니다."),
    FILE_400_REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "FILE_400_REQUIRED_FIELD_MISSING", "파일과 이름은 필수입니다."),
    FILE_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "FILE_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    FILE_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    FILE_404_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_404_SCHEDULE_NOT_FOUND", "존재하지 않는 일정입니다."),
    FILE_404_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_404_NOT_FOUND", "존재하지 않는 파일입니다."),
    FILE_404_NO_DOWNLOADABLE_FILES(HttpStatus.NOT_FOUND, "FILE_404_NO_DOWNLOADABLE_FILES", "다운로드할 파일이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
