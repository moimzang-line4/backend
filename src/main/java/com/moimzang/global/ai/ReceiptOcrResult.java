package com.moimzang.global.ai;

// 영수증 인식 원시 결과. 못 찾은 필드는 null로 온다(프롬프트로 강제). 형식·범위 검증과
// 정규화는 호출하는 쪽의 책임이다
public record ReceiptOcrResult(String storeName, String transactedAt, Integer totalAmount) {
}