package com.moimzang.domain.settlement.repository;

import static com.moimzang.domain.attendance.entity.QScheduleAttendee.scheduleAttendee;
import static com.moimzang.domain.attendance.entity.QScheduleRound.scheduleRound;
import static com.moimzang.domain.settlement.entity.QSettlementTarget.settlementTarget;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.enums.PaymentStatusFilter;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class SettlementTargetRepositoryImpl implements SettlementTargetRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 참가자 이름·학과와 차수 번호를 모두 응답에 써야 해서 둘 다 fetch join한다 —
    // 없으면 대상자 수만큼 추가 쿼리가 나간다
    @Override
    public List<SettlementTarget> findByScheduleForAssistant(Long scheduleId, Integer roundNo) {
        return queryFactory.selectFrom(settlementTarget)
                .join(settlementTarget.scheduleRound, scheduleRound).fetchJoin()
                .join(settlementTarget.scheduleAttendee, scheduleAttendee).fetchJoin()
                .where(scheduleRound.schedule.id.eq(scheduleId))
                .where(roundNoEq(roundNo))
                .orderBy(scheduleAttendee.name.asc(), scheduleRound.roundNo.asc())
                .fetch();
    }

    @Override
    public List<SettlementTarget> search(Long roundId, String keyword, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        return queryFactory.selectFrom(settlementTarget)
                .join(settlementTarget.scheduleAttendee, scheduleAttendee).fetchJoin()
                .where(settlementTarget.scheduleRound.id.eq(roundId))
                .where(nameContains(keyword))
                .where(cursorCondition(cursorCreatedAt, cursorId))
                .orderBy(settlementTarget.createdAt.asc(), settlementTarget.id.asc())
                .limit(limit)
                .fetch();
    }

    // 입금 확인·되돌리기(API-040)·입금 명단 삭제(API-041)가 재계산에 쓸 scheduleRound까지
    // fetch join한다
    @Override
    public List<SettlementTarget> findByScheduleAndAttendee(Long scheduleId, Long attendeeId) {
        return queryFactory.selectFrom(settlementTarget)
                .join(settlementTarget.scheduleRound, scheduleRound).fetchJoin()
                .where(scheduleRound.schedule.id.eq(scheduleId), settlementTarget.scheduleAttendee.id.eq(attendeeId))
                .fetch();
    }

    @Override
    public List<PaymentSummary> searchPaymentSummaries(Long scheduleId, PaymentStatusFilter status, String keyword,
                                                         LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        NumberExpression<Integer> unpaidRoundCount = new CaseBuilder()
                .when(settlementTarget.paymentStatus.ne(PaymentStatus.PAID)).then(1).otherwise(0)
                .sum();

        List<Tuple> rows = queryFactory
                .select(scheduleAttendee, settlementTarget.billedAmount.sum(), unpaidRoundCount)
                .from(settlementTarget)
                .join(settlementTarget.scheduleAttendee, scheduleAttendee)
                .where(scheduleAttendee.schedule.id.eq(scheduleId))
                .where(nameContains(keyword))
                .where(attendeeCursorCondition(cursorCreatedAt, cursorId))
                .groupBy(scheduleAttendee.id)
                .having(statusHaving(status, unpaidRoundCount))
                .orderBy(scheduleAttendee.createdAt.asc(), scheduleAttendee.id.asc())
                .limit(limit)
                .fetch();

        return rows.stream()
                .map(row -> {
                    int unpaidCount = row.get(unpaidRoundCount);
                    PaymentStatus paymentStatus = unpaidCount == 0 ? PaymentStatus.PAID : PaymentStatus.UNPAID;
                    return new PaymentSummary(row.get(scheduleAttendee), row.get(settlementTarget.billedAmount.sum()), paymentStatus);
                })
                .toList();
    }

    private BooleanExpression statusHaving(PaymentStatusFilter status, NumberExpression<Integer> unpaidRoundCount) {
        if (status == null || status == PaymentStatusFilter.ALL) {
            return null;
        }
        return status == PaymentStatusFilter.PAID ? unpaidRoundCount.eq(0) : unpaidRoundCount.gt(0);
    }

    // schedule_attendee.createdAt은 그 사람의 모든 settlement_target 행에서 같은 값이라
    // group by 전에 걸어도 결과가 같다
    private BooleanExpression attendeeCursorCondition(LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return scheduleAttendee.createdAt.gt(cursorCreatedAt)
                .or(scheduleAttendee.createdAt.eq(cursorCreatedAt).and(scheduleAttendee.id.gt(cursorId)));
    }

    private BooleanExpression nameContains(String keyword) {
        return StringUtils.hasText(keyword) ? scheduleAttendee.name.containsIgnoreCase(keyword) : null;
    }

    // 다음 페이지 cursor 조건 — (createdAt, id) 복합 비교. 오름차순이라 gt를 쓴다.
    // 첫 페이지(cursorId 없음)는 조건 없음
    private BooleanExpression cursorCondition(LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return settlementTarget.createdAt.gt(cursorCreatedAt)
                .or(settlementTarget.createdAt.eq(cursorCreatedAt).and(settlementTarget.id.gt(cursorId)));
    }

    private BooleanExpression roundNoEq(Integer roundNo) {
        return roundNo == null ? null : scheduleRound.roundNo.eq(roundNo);
    }
}
