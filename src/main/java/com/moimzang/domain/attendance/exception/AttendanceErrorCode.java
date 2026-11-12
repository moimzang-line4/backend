package com.moimzang.domain.attendance.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttendanceErrorCode implements ErrorCode {

    ATTENDANCE_409_DUPLICATE_NAME(HttpStatus.CONFLICT, "ATTENDANCE_409_DUPLICATE_NAME", "같은 이름의 사람이 있어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
