package com.moimzang.domain.schedule.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {

    SCHEDULE_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "SCHEDULE_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    SCHEDULE_400_END_BEFORE_START(HttpStatus.BAD_REQUEST, "SCHEDULE_400_END_BEFORE_START", "종료 시간이 시작 시간보다 빠릅니다."),
    SCHEDULE_404_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_NOT_FOUND", "존재하지 않는 일정입니다."),
    SCHEDULE_404_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_CATEGORY_NOT_FOUND", "존재하지 않는 일정 카테고리입니다."),
    SCHEDULE_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    SPACE_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "SPACE_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    SPACE_404_AGENDA_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_404_AGENDA_NOT_FOUND", "존재하지 않는 안건입니다."),
    SPACE_404_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "SPACE_404_LINK_NOT_FOUND", "존재하지 않는 안건 연결입니다."),
    SPACE_409_AGENDA_ALREADY_LINKED(HttpStatus.CONFLICT, "SPACE_409_AGENDA_ALREADY_LINKED", "이미 연결된 안건입니다."),
    SCHEDULE_409_CATEGORY_DUPLICATED(HttpStatus.CONFLICT, "SCHEDULE_409_CATEGORY_DUPLICATED", "이미 있는 카테고리입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
