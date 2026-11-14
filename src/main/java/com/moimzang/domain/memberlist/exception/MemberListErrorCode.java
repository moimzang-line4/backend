package com.moimzang.domain.memberlist.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberListErrorCode implements ErrorCode {

    MEMBER_LIST_404_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_LIST_404_NOT_FOUND", "존재하지 않는 명단방입니다."),
    MEMBER_LIST_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_LIST_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
