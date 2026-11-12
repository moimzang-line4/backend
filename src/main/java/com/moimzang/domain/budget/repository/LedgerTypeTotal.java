package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.enums.LedgerType;

// 장부 유형별 집계 결과(수입/지출 각각의 누적액과 건수)
public record LedgerTypeTotal(LedgerType type, long totalAmount, long count) {
}
