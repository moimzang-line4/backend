package com.moimzang.domain.memberlist.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberListErrorCode implements ErrorCode {

    MEMBER_LIST_404_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_LIST_404_NOT_FOUND", "존재하지 않는 명단방입니다."),
    MEMBER_LIST_404_GROUP_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_LIST_404_GROUP_TERM_NOT_FOUND", "존재하지 않는 기수입니다."),
    MEMBER_404_JOIN_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_404_JOIN_LINK_NOT_FOUND", "존재하지 않는 등록 링크입니다."),
    MEMBER_410_JOIN_CLOSED(HttpStatus.GONE, "MEMBER_410_JOIN_CLOSED", "등록이 마감된 명단입니다."),
    MEMBER_400_PRIVACY_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "MEMBER_400_PRIVACY_CONSENT_REQUIRED", "개인정보 수집·이용에 동의해 주세요."),
    MEMBER_400_REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "MEMBER_400_REQUIRED_FIELD_MISSING", "필수 항목을 입력해 주세요."),
    MEMBER_409_ALREADY_REGISTERED(HttpStatus.CONFLICT, "MEMBER_409_ALREADY_REGISTERED", "이미 등록된 부원입니다."),
    MEMBER_400_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "MEMBER_400_INVALID_CURSOR", "잘못된 cursor입니다."),
    MEMBER_404_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_404_NOT_FOUND", "존재하지 않는 부원입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
