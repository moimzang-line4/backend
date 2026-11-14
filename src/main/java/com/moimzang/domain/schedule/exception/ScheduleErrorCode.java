package com.moimzang.domain.schedule.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {

    SCHEDULE_400_END_BEFORE_START(HttpStatus.BAD_REQUEST, "SCHEDULE_400_END_BEFORE_START", "종료 시간이 시작 시간보다 빠릅니다."),
    SCHEDULE_404_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_NOT_FOUND", "존재하지 않는 일정입니다."),
    SCHEDULE_404_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_CATEGORY_NOT_FOUND", "존재하지 않는 일정 카테고리입니다."),
    SCHEDULE_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    SCHEDULE_409_CATEGORY_DUPLICATED(HttpStatus.CONFLICT, "SCHEDULE_409_CATEGORY_DUPLICATED", "이미 있는 카테고리입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
