package com.moimzang.domain.schedule.repository;

import static com.moimzang.domain.schedule.entity.QSchedule.schedule;
import static com.moimzang.domain.schedule.entity.QScheduleCategory.scheduleCategory;

import com.moimzang.domain.schedule.entity.Schedule;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 여러 날에 걸친 일정도 캘린더 칸마다 보여야 해서 시작이 아니라 "기간과 겹치는지"로 거른다.
    // 카테고리명을 함께 내려줘야 해서 scheduleCategory를 fetch join한다
    @Override
    public List<Schedule> searchCalendar(Long groupTermId, LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                         LocalDateTime cursorStartAt, Long cursorId, int limit) {
        return queryFactory.selectFrom(schedule)
                .leftJoin(schedule.scheduleCategory, scheduleCategory).fetchJoin()
                .where(schedule.groupTerm.id.eq(groupTermId))
                .where(schedule.startAt.loe(rangeEnd), schedule.endAt.goe(rangeStart))
                .where(calendarCursor(cursorStartAt, cursorId))
                .orderBy(schedule.startAt.asc(), schedule.id.asc())
                .limit(limit)
                .fetch();
    }

    // 운영 공간을 켜지 않은 일정은 운영 메뉴가 없어 이 카드에 올리지 않는다.
    // 진행 중인 일정도 아직 준비할 게 남아 있어 종료 시각 기준으로 거른다
    @Override
    public List<Schedule> findUpcomingOperationSchedules(Long groupTermId, LocalDateTime now, int limit) {
        return queryFactory.selectFrom(schedule)
                .leftJoin(schedule.scheduleCategory, scheduleCategory).fetchJoin()
                .where(schedule.groupTerm.id.eq(groupTermId),
                        schedule.hasOperationSpace.isTrue(),
                        schedule.endAt.gt(now))
                .orderBy(schedule.startAt.asc(), schedule.id.asc())
                .limit(limit)
                .fetch();
    }

    // 카테고리명을 함께 내려줘야 해서 scheduleCategory를 fetch join한다.
    // 최근 일정부터 보여주는 게 "다가오는 일정" 류 질문에 유리해 startAt 내림차순으로 정렬한다
    @Override
    public List<Schedule> searchForAssistant(Long groupTermId, String keyword, LocalDate from, LocalDate to, int limit) {
        return queryFactory.selectFrom(schedule)
                .leftJoin(schedule.scheduleCategory, scheduleCategory).fetchJoin()
                .where(schedule.groupTerm.id.eq(groupTermId))
                .where(titleContains(keyword), startsOnOrAfter(from), startsOnOrBefore(to))
                .orderBy(schedule.startAt.desc(), schedule.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<Schedule> findForAssistant(Long groupTermId, Long scheduleId) {
        return Optional.ofNullable(queryFactory.selectFrom(schedule)
                .leftJoin(schedule.scheduleCategory, scheduleCategory).fetchJoin()
                .where(schedule.id.eq(scheduleId), schedule.groupTerm.id.eq(groupTermId))
                .fetchOne());
    }

    // (startAt, id) 복합 비교. 오름차순이라 gt를 쓰고, 같은 시각에 시작하는 일정은 id로 순서를 가른다
    private BooleanExpression calendarCursor(LocalDateTime cursorStartAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return schedule.startAt.gt(cursorStartAt)
                .or(schedule.startAt.eq(cursorStartAt).and(schedule.id.gt(cursorId)));
    }

    private BooleanExpression titleContains(String keyword) {
        return StringUtils.hasText(keyword) ? schedule.title.containsIgnoreCase(keyword) : null;
    }

    // 기간 필터는 일정 시작 시각 기준이다 — 날짜만 받아 그날 00:00 이후를 포함한다
    private BooleanExpression startsOnOrAfter(LocalDate from) {
        return from == null ? null : schedule.startAt.goe(from.atStartOfDay());
    }

    // to는 그날 전체를 포함해야 하므로 23:59:59.999999999까지 본다
    private BooleanExpression startsOnOrBefore(LocalDate to) {
        return to == null ? null : schedule.startAt.loe(to.atTime(LocalTime.MAX));
    }
}
