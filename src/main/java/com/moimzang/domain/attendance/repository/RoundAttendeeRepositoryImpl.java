package com.moimzang.domain.attendance.repository;

import static com.moimzang.domain.attendance.entity.QRoundAttendee.roundAttendee;
import static com.moimzang.domain.attendance.entity.QScheduleAttendee.scheduleAttendee;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class RoundAttendeeRepositoryImpl implements RoundAttendeeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RoundAttendee> search(Long roundId, Boolean isCheckedIn, String keyword,
                                       LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        return queryFactory.selectFrom(roundAttendee)
                .leftJoin(roundAttendee.scheduleAttendee, scheduleAttendee).fetchJoin()
                .where(baseConditions(roundId, isCheckedIn, keyword))
                .where(cursorCondition(cursorCreatedAt, cursorId))
                .orderBy(roundAttendee.createdAt.asc(), roundAttendee.id.asc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression[] baseConditions(Long roundId, Boolean isCheckedIn, String keyword) {
        return new BooleanExpression[]{
                roundAttendee.scheduleRound.id.eq(roundId),
                isCheckedInEq(isCheckedIn),
                nameContains(keyword)
        };
    }

    // status 필터 조건 — AttendanceStatusFilter.ALL이면 Service가 null을 넘겨 조건을 뺀다
    private BooleanExpression isCheckedInEq(Boolean isCheckedIn) {
        return isCheckedIn == null ? null : roundAttendee.isCheckedIn.eq(isCheckedIn);
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
        return roundAttendee.createdAt.gt(cursorCreatedAt)
                .or(roundAttendee.createdAt.eq(cursorCreatedAt).and(roundAttendee.id.gt(cursorId)));
    }
}
