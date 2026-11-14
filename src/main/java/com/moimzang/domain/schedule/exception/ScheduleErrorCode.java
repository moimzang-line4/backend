package com.moimzang.domain.schedule.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {

    SCHEDULE_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    SCHEDULE_409_CATEGORY_DUPLICATED(HttpStatus.CONFLICT, "SCHEDULE_409_CATEGORY_DUPLICATED", "이미 있는 카테고리입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
