package com.moimzang.domain.budget.repository;

import static com.moimzang.domain.budget.entity.QLedgerEntry.ledgerEntry;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
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

    // 수입만 있고 지출이 없는 기수처럼 한쪽 유형이 없으면 group by 결과에 행 자체가 없다 —
    // 호출하는 쪽에서 0으로 채운다
    @Override
    public List<LedgerTypeTotal> aggregateByType(Long groupTermId) {
        List<Tuple> totals = queryFactory
                .select(ledgerEntry.type, ledgerEntry.amount.sum(), ledgerEntry.count())
                .from(ledgerEntry)
                .where(ledgerEntry.groupTerm.id.eq(groupTermId))
                .groupBy(ledgerEntry.type)
                .fetch();

        return totals.stream()
                .map(tuple -> new LedgerTypeTotal(
                        tuple.get(ledgerEntry.type),
                        tuple.get(ledgerEntry.amount.sum()),
                        tuple.get(ledgerEntry.count())))
                .toList();
    }
}
