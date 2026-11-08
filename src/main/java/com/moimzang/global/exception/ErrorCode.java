package com.moimzang.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 계약. 공통은 CommonErrorCode, 도메인별은
 * domain 하위 각 도메인의 exception 패키지에 둔다(예: settlement 도메인은 SettlementErrorCode).
 *
 * HTTP 상태는 이 인터페이스가 결정한다 — 컨트롤러나 핸들러가 따로 판단하지 않는다.
 */
public interface ErrorCode {

    String getCode();

    String getMessage();

    HttpStatus getStatus();
}
