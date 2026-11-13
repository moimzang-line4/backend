package com.moimzang.domain.meeting.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MeetingErrorCode implements ErrorCode {

    MEETING_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "MEETING_400_INVALID_CURSOR", "cursor 형식이 올바르지 않습니다."),
    MEETING_404_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_CATEGORY_NOT_FOUND", "존재하지 않는 회의 카테고리입니다."),
    MEETING_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    MEETING_404_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_NOT_FOUND", "존재하지 않는 회의입니다."),
    MEETING_404_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_SCHEDULE_NOT_FOUND", "존재하지 않는 일정입니다."),
    MEETING_409_ALREADY_ENDED(HttpStatus.CONFLICT, "MEETING_409_ALREADY_ENDED", "이미 종료된 회의입니다."),
    AGENDA_404_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENDA_404_NOT_FOUND", "존재하지 않는 안건입니다."),
    AGENDA_409_AT_LEAST_ONE_REQUIRED(HttpStatus.CONFLICT, "AGENDA_409_AT_LEAST_ONE_REQUIRED", "안건이 1개만 남아 삭제할 수 없습니다."),
    AGENDA_409_ALREADY_COMPLETED(HttpStatus.CONFLICT, "AGENDA_409_ALREADY_COMPLETED", "이미 완료된 안건은 상태를 되돌릴 수 없습니다."),
    MEETING_502_SUMMARY_FAILED(HttpStatus.BAD_GATEWAY, "MEETING_502_SUMMARY_FAILED", "AI 요약에 실패했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
