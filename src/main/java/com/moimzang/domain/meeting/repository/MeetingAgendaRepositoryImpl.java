package com.moimzang.domain.meeting.repository;

import static com.moimzang.domain.meeting.entity.QMeetingAgenda.meetingAgenda;

import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

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
