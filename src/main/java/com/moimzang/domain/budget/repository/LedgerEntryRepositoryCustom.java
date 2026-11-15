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

    // 같은 그룹에서 이 기수보다 먼저 생성된 기수(group_term_id가 더 작은 기수)들의 장부만 집계한다.
    // 장부 엑셀 다운로드(API-075)의 "초기 잔액"(이 기수가 시작하기 전까지의 그룹 누적 잔액) 계산용
    List<LedgerTypeTotal> aggregateByTypeForGroupBeforeTerm(Long groupId, Long termId);

    // 기수의 장부 내역을 cursor 페이지네이션으로 조회한다(API-069, 정렬: entryDate desc, id desc)
    List<LedgerEntry> search(Long groupTermId, LedgerType type, LocalDate cursorEntryDate, Long cursorId, int limit);

    // 장부 엑셀 다운로드(API-075)용 — 그 기수 전체 내역을 페이지네이션 없이 한 번에 가져온다.
    // 정렬: entryDate asc, id asc(API-069의 내림차순과 방향만 반대이고, id를 2차 정렬로 둬서
    // 같은 날짜 내역의 순서가 재호출해도 항상 같다)
    List<LedgerEntry> findAllForExport(Long groupTermId);
}
