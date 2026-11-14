package com.moimzang.global.ai;

// 영수증 인식 호출 실패(네트워크·타임아웃·응답 파싱 전부 포함)를 이 타입 하나로 통일해서 올린다.
// 도메인 ErrorCode로의 번역은 호출하는 쪽(Service)의 책임이다
public class ReceiptOcrException extends RuntimeException {

    public ReceiptOcrException(String message) {
        super(message);
    }

    public ReceiptOcrException(String message, Throwable cause) {
        super(message, cause);
    }
}