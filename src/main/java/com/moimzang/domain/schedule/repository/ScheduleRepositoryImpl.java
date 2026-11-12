package com.moimzang.domain.schedule.repository;

import static com.moimzang.domain.schedule.entity.QSchedule.schedule;
import static com.moimzang.domain.schedule.entity.QScheduleCategory.scheduleCategory;

import com.moimzang.domain.schedule.entity.Schedule;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

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
