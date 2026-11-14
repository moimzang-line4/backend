package com.moimzang.domain.note.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NoteErrorCode implements ErrorCode {

    NOTE_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "NOTE_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    NOTE_404_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTE_404_NOT_FOUND", "존재하지 않는 메모입니다."),
    NOTE_404_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTE_404_SCHEDULE_NOT_FOUND", "존재하지 않는 일정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
