package com.moimzang.domain.budget.enums;

// 자동기록 종류. 같은 일정에서 최대 3종 지출 + 1종 수입이 함께 생길 수 있어(차수가 아니라 일정
// 단위로 합산한다) type(수입/지출)만으로는 구분이 안 돼 따로 둔다. 직접 입력(API-070)은 이 값이
// null이고, 그 일정이 삭제돼 schedule_id가 고아 상태가 된 뒤에도 이 값은 남아 "이 항목은
// 자동기록이었다"를 판단하는 유일한 근거가 된다(API-072의 "자동기록은 고아 여부와 무관하게 항상
// 수정 불가" 규칙에 필요)
public enum LedgerAutoCategory {

    // 영수증 지출(지원금 제외) — 부원 부담분 합계
    RECEIPT_EXPENSE,

    // 동아리 지원금 지출
    CLUB_SUBSIDY_EXPENSE,

    // 100원 단위 버림 차액 "추가 동아리 지원금" 지출
    ROUNDING_SUBSIDY_EXPENSE,

    // 부원 납부 수입
    PAYMENT_INCOME
}
