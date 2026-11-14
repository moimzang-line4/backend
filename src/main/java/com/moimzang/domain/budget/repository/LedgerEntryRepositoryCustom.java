package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import java.time.LocalDate;
import java.util.List;

public interface LedgerEntryRepositoryCustom {

    // 기수의 장부 항목을 최근 거래일 순으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<LedgerEntry> findForAssistant(Long groupTermId, int limit);

    // 수입·지출 누적액과 건수를 유형별로 집계한다(그 기수 범위).
    // 항목 목록이 상한에 잘려도 합계는 맞아야 하므로 DB에서 전체를 집계한다
    List<LedgerTypeTotal> aggregateByType(Long groupTermId);

    // 수입·지출 누적액과 건수를 유형별로 집계한다(그룹 전체 범위, 기수가 바뀌어도 이월).
    // API-069의 currentBalance·totalIncome·totalExpense가 전부 이 결과로 계산된다
    List<LedgerTypeTotal> aggregateByTypeForGroup(Long groupId);

    // 기수의 장부 내역을 cursor 페이지네이션으로 조회한다(API-069, 정렬: entryDate desc, id desc)
    List<LedgerEntry> search(Long groupTermId, LedgerType type, LocalDate cursorEntryDate, Long cursorId, int limit);
}
