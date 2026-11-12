package com.moimzang.domain.attendance.repository;

import static com.moimzang.domain.attendance.entity.QRoundAttendee.roundAttendee;
import static com.moimzang.domain.attendance.entity.QScheduleAttendee.scheduleAttendee;
import static com.moimzang.domain.attendance.entity.QScheduleRound.scheduleRound;

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

    // 차수 번호와 참가자 이름을 모두 응답에 써야 해서 둘 다 fetch join한다.
    // scheduleId는 모델이 만들어낸 값이라 다른 기수의 일정일 수 있어 기수 조건을 함께 건다
    @Override
    public List<RoundAttendee> findByScheduleForAssistant(Long groupTermId, Long scheduleId, Integer roundNo) {
        return queryFactory.selectFrom(roundAttendee)
                .join(roundAttendee.scheduleRound, scheduleRound).fetchJoin()
                .join(roundAttendee.scheduleAttendee, scheduleAttendee).fetchJoin()
                .where(scheduleRound.schedule.id.eq(scheduleId),
                        scheduleRound.schedule.groupTerm.id.eq(groupTermId))
                .where(roundNoEq(roundNo))
                .orderBy(scheduleRound.roundNo.asc(), scheduleAttendee.name.asc())
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

    private BooleanExpression roundNoEq(Integer roundNo) {
        return roundNo == null ? null : scheduleRound.roundNo.eq(roundNo);
    }
}
