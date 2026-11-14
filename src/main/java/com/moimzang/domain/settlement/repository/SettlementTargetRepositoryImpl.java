package com.moimzang.domain.settlement.repository;

import static com.moimzang.domain.attendance.entity.QScheduleAttendee.scheduleAttendee;
import static com.moimzang.domain.attendance.entity.QScheduleRound.scheduleRound;
import static com.moimzang.domain.settlement.entity.QSettlementTarget.settlementTarget;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.querydsl.core.types.dsl.BooleanExpression;
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
