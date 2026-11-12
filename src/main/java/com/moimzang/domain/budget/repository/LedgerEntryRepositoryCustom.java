package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.entity.LedgerEntry;
import java.util.List;

public interface LedgerEntryRepositoryCustom {

    // 기수의 장부 항목을 최근 거래일 순으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<LedgerEntry> findForAssistant(Long groupTermId, int limit);

    // 수입·지출 누적액과 건수를 유형별로 집계한다.
    // 항목 목록이 상한에 잘려도 합계는 맞아야 하므로 DB에서 전체를 집계한다
    List<LedgerTypeTotal> aggregateByType(Long groupTermId);
}
