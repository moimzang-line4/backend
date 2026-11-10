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
    MEETING_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
