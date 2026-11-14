package com.moimzang.domain.budget.repository;

import static com.moimzang.domain.budget.entity.QLedgerEntry.ledgerEntry;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LedgerEntryRepositoryImpl implements LedgerEntryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<LedgerEntry> findForAssistant(Long groupTermId, int limit) {
        return queryFactory.selectFrom(ledgerEntry)
                .where(ledgerEntry.groupTerm.id.eq(groupTermId))
                .orderBy(ledgerEntry.entryDate.desc(), ledgerEntry.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<LedgerTypeTotal> aggregateByType(Long groupTermId) {
        return aggregateByTypeWhere(ledgerEntry.groupTerm.id.eq(groupTermId));
    }

    @Override
    public List<LedgerTypeTotal> aggregateByTypeForGroup(Long groupId) {
        return aggregateByTypeWhere(ledgerEntry.groupTerm.group.id.eq(groupId));
    }

    // 수입만 있고 지출이 없는 기수(또는 그룹)처럼 한쪽 유형이 없으면 group by 결과에 행 자체가
    // 없다 — 호출하는 쪽에서 0으로 채운다
    private List<LedgerTypeTotal> aggregateByTypeWhere(BooleanExpression condition) {
        List<Tuple> totals = queryFactory
                .select(ledgerEntry.type, ledgerEntry.amount.sum(), ledgerEntry.count())
                .from(ledgerEntry)
                .where(condition)
                .groupBy(ledgerEntry.type)
                .fetch();

        return totals.stream()
                .map(tuple -> new LedgerTypeTotal(
                        tuple.get(ledgerEntry.type),
                        tuple.get(ledgerEntry.amount.sum()),
                        tuple.get(ledgerEntry.count())))
                .toList();
    }

    @Override
    public List<LedgerEntry> search(Long groupTermId, LedgerType type, LocalDate cursorEntryDate, Long cursorId, int limit) {
        return queryFactory.selectFrom(ledgerEntry)
                .where(ledgerEntry.groupTerm.id.eq(groupTermId))
                .where(typeEq(type))
                .where(cursorCondition(cursorEntryDate, cursorId))
                .orderBy(ledgerEntry.entryDate.desc(), ledgerEntry.id.desc())
                .limit(limit)
                .fetch();
    }

    // type 필터 조건을 만든다. 값이 없으면(ALL) 조건을 걸지 않는다
    private BooleanExpression typeEq(LedgerType type) {
        return type == null ? null : ledgerEntry.type.eq(type);
    }

    // 다음 페이지 cursor 조건 — (entryDate, id) 복합 비교. 내림차순이라 lt를 쓴다.
    // 첫 페이지(cursorId 없음)는 조건 없음
    private BooleanExpression cursorCondition(LocalDate cursorEntryDate, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return ledgerEntry.entryDate.lt(cursorEntryDate)
                .or(ledgerEntry.entryDate.eq(cursorEntryDate).and(ledgerEntry.id.lt(cursorId)));
    }
}
