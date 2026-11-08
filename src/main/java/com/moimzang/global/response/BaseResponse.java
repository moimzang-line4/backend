package com.moimzang.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API의 공통 응답 봉투. 성공·실패 모두 이 형태로 내려간다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BaseResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    public static <T> BaseResponse<T> success(CommonErrorCode code, T data) {
        return new BaseResponse<>(true, code.getCode(), code.getMessage(), data);
    }

    public static <T> BaseResponse<T> success(T data) {
        return success(CommonErrorCode.OK, data);
    }

    public static BaseResponse<Void> success() {
        return success(CommonErrorCode.OK, null);
    }

    public static BaseResponse<Void> fail(ErrorCode errorCode) {
        return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 검증 실패처럼 어느 필드가 틀렸는지 알려줘야 할 때만 메시지를 바꾼다. */
    public static BaseResponse<Void> fail(ErrorCode errorCode, String message) {
        return new BaseResponse<>(false, errorCode.getCode(), message, null);
    }
}
