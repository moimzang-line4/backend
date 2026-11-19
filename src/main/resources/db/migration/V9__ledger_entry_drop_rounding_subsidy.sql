-- 장부 자동기록이 영수증 등록 즉시(정산 명단과 무관하게) 반영되도록 바뀌면서, 영수증 지출에
-- 100원 버림 차액을 포함해 계산하므로 ROUNDING_SUBSIDY_EXPENSE가 더 이상 쓰이지 않는다. 남은
-- 자동기록(RECEIPT_EXPENSE·CLUB_SUBSIDY_EXPENSE·PAYMENT_INCOME)은 다음 영수증 등록·수정·삭제
-- 또는 입금 확인·되돌리기, 혹은 정산 내역 새로고침(API-074) 호출 시점에 새 계산식으로 자동
-- 갱신된다.
DELETE FROM ledger_entry WHERE auto_category = 'ROUNDING_SUBSIDY_EXPENSE';
