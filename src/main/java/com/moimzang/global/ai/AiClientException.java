package com.moimzang.global.ai;

// AI 호출 실패(네트워크·타임아웃·응답 파싱 전부 포함)를 벤더 무관하게 이 타입 하나로 통일해서 올린다.
// 도메인 ErrorCode로의 번역은 호출하는 쪽(Service)의 책임이다 — 이 예외 자체는 도메인을 모른다
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
