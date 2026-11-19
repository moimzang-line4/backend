package com.moimzang.domain.budget.enums;

// 자동기록 종류. 같은 일정에서 최대 2종 지출 + 1종 수입이 함께 생길 수 있어(차수가 아니라 일정
// 단위로 합산한다) type(수입/지출)만으로는 구분이 안 돼 따로 둔다. 직접 입력(API-070)은 이 값이
// null이고, 그 일정이 삭제돼 schedule_id가 고아 상태가 된 뒤에도 이 값은 남아 "이 항목은
// 자동기록이었다"를 판단하는 유일한 근거가 된다(API-072의 "자동기록은 고아 여부와 무관하게 항상
// 수정 불가" 규칙에 필요). label은 자동 기록 itemName을 만들 때 일정 제목 뒤에 붙인다
public enum LedgerAutoCategory {

    // 영수증 지출 — 지원금을 뺀 나머지(100원 버림 차액 포함) 전부
    RECEIPT_EXPENSE(LedgerType.EXPENSE, "영수증 지출"),

    // 동아리 지원금 지출
    CLUB_SUBSIDY_EXPENSE(LedgerType.EXPENSE, "동아리 지원금"),

    // 부원 납부 수입
    PAYMENT_INCOME(LedgerType.INCOME, "부원 납부");

    private final LedgerType type;
    private final String label;

    LedgerAutoCategory(LedgerType type, String label) {
        this.type = type;
        this.label = label;
    }

    public LedgerType type() {
        return type;
    }

    public String label() {
        return label;
    }
}
