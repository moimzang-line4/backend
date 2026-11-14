package com.moimzang.domain.group.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GroupErrorCode implements ErrorCode {

    GROUP_404_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP_404_NOT_FOUND", "존재하지 않는 그룹입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
