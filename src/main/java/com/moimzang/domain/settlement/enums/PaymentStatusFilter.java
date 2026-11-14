package com.moimzang.domain.settlement.enums;

// 입금 현황 조회(API-042)용 상태 필터. 사람 단위로는 "모든 차수가 PAID여야 PAID"라는 집계
// 조건이라 PaymentStatus 컬럼 값과 1:1로 매칭되지 않아 별도 enum으로 분리했다
public enum PaymentStatusFilter {
    UNPAID,
    PAID,
    ALL
}
