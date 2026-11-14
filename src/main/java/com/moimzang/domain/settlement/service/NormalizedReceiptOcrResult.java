package com.moimzang.domain.settlement.service;

import java.time.LocalDateTime;

// ReceiptOcrResultNormalizer가 돌려주는 검증된 결과. 못 찾았거나 형식·범위가 비정상인 필드는
// null이다(api-spec: "인식 실패 항목은 null")
public record NormalizedReceiptOcrResult(String storeName, LocalDateTime transactedAt, Integer totalAmount) {
}