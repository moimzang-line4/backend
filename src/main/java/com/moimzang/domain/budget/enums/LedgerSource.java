package com.moimzang.domain.budget.enums;

// 장부 내역의 출처. auto_category 유무로 판별한다(decisions #65)
public enum LedgerSource {

    // 자동 기록(정산 내역 새로고침이 생성)
    RECEIPT,

    // 직접 입력
    MANUAL
}
