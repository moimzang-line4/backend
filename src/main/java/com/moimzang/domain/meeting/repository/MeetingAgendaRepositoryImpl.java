package com.moimzang.domain.meeting.repository;

import static com.moimzang.domain.meeting.entity.QMeeting.meeting;
import static com.moimzang.domain.meeting.entity.QMeetingAgenda.meetingAgenda;
import static com.moimzang.domain.schedule.entity.QScheduleAgendaLink.scheduleAgendaLink;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MeetingAgendaRepositoryImpl implements MeetingAgendaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 회의 ID 목록으로 안건 개수를 그룹 집계한다. 안건이 0개인 회의는 group by 결과에 행 자체가
    // 없다 — 호출하는 쪽에서 getOrDefault(id, 0L)로 채운다
    @Override
    public Map<Long, Long> countByMeetingIds(List<Long> meetingIds) {
        return countByMeetingIds(meetingIds, null);
    }

    // 완료 안건만 센다. 전체 개수와 같은 방식이라 조건만 더해 재사용한다
    @Override
    public Map<Long, Long> countCompletedByMeetingIds(List<Long> meetingIds) {
        return countByMeetingIds(meetingIds, AgendaStatus.COMPLETED);
    }

    // 회의 제목도 응답에 쓰므로 meeting을 fetch join한다. 정렬은 회의 묶음을 유지한다 —
    // 최근에 만든 회의가 먼저 오고, 한 회의 안에서는 "안건1 → 안건2" 순서 그대로다
    @Override
    public List<MeetingAgenda> searchLinkable(Long groupTermId, String keyword,
                                               Long cursorMeetingId, Long cursorAgendaId, int limit) {
        return queryFactory.selectFrom(meetingAgenda)
                .join(meetingAgenda.meeting, meeting).fetchJoin()
                .where(linkableConditions(groupTermId, keyword))
                .where(cursorCondition(cursorMeetingId, cursorAgendaId))
                .orderBy(meeting.id.desc(), meetingAgenda.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countLinkable(Long groupTermId, String keyword) {
        Long count = queryFactory.select(meetingAgenda.count())
                .from(meetingAgenda)
                .join(meetingAgenda.meeting, meeting)
                .where(linkableConditions(groupTermId, keyword))
                .fetchOne();

        return Optional.ofNullable(count).orElse(0L);
    }

    // 후보 조건 — 기수가 같고, 회의가 어느 일정에도 속하지 않았고, 이미 연결된 안건이 아니어야 한다.
    // 연결된 안건 ID를 전부 읽어 in 조건으로 거르면 안건 수에 비례해 커지므로 notExists로 건다
    private BooleanBuilder linkableConditions(Long groupTermId, String keyword) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(meeting.groupTerm.id.eq(groupTermId));
        conditions.and(meeting.schedule.isNull());
        conditions.and(JPAExpressions.selectOne()
                .from(scheduleAgendaLink)
                .where(scheduleAgendaLink.meetingAgenda.id.eq(meetingAgenda.id))
                .notExists());
        conditions.and(contentContains(keyword));
        return conditions;
    }

    // 안건 내용 검색 조건을 만든다. 값이 없으면 조건을 걸지 않는다
    private BooleanExpression contentContains(String keyword) {
        return StringUtils.hasText(keyword) ? meetingAgenda.content.containsIgnoreCase(keyword) : null;
    }

    // 다음 페이지 cursor 조건 — (meeting.id 내림차순, agenda.id 오름차순) 복합 비교.
    // 첫 페이지(cursor 없음)는 조건 없음
    private BooleanExpression cursorCondition(Long cursorMeetingId, Long cursorAgendaId) {
        if (cursorMeetingId == null || cursorAgendaId == null) {
            return null;
        }
        return meeting.id.lt(cursorMeetingId)
                .or(meeting.id.eq(cursorMeetingId).and(meetingAgenda.id.gt(cursorAgendaId)));
    }

    private Map<Long, Long> countByMeetingIds(List<Long> meetingIds, AgendaStatus status) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> counts = queryFactory
                .select(meetingAgenda.meeting.id, meetingAgenda.count())
                .from(meetingAgenda)
                .where(meetingAgenda.meeting.id.in(meetingIds))
                .where(status == null ? null : meetingAgenda.status.eq(status))
                .groupBy(meetingAgenda.meeting.id)
                .fetch();

        return counts.stream().collect(Collectors.toMap(
                tuple -> tuple.get(meetingAgenda.meeting.id),
                tuple -> tuple.get(meetingAgenda.count())));
    }
}
