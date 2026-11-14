package com.moimzang.domain.settlement.service;

import com.moimzang.global.ai.ReceiptOcrResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

// Gemini가 돌려준 원시 인식 결과를 검증·정규화한다. 형식이 깨졌거나 비정상인 값은 호출 실패(502)가
// 아니라 그 필드만 null로 내려준다 — api-spec이 "인식 실패 항목은 null"이라고 명시한 대상은
// 필드 단위다
@Component
public class ReceiptOcrResultNormalizer {

    public NormalizedReceiptOcrResult normalize(ReceiptOcrResult raw) {
        return new NormalizedReceiptOcrResult(
                normalizeStoreName(raw.storeName()),
                normalizeTransactedAt(raw.transactedAt()),
                normalizeTotalAmount(raw.totalAmount()));
    }

    private String normalizeStoreName(String storeName) {
        if (storeName == null || storeName.isBlank()) {
            return null;
        }
        return storeName.trim();
    }

    private LocalDateTime normalizeTransactedAt(String transactedAt) {
        if (transactedAt == null || transactedAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(transactedAt.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer normalizeTotalAmount(Integer totalAmount) {
        if (totalAmount == null || totalAmount <= 0) {
            return null;
        }
        return totalAmount;
    }
}
