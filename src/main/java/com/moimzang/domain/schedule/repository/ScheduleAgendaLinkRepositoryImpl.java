package com.moimzang.domain.schedule.repository;

import static com.moimzang.domain.meeting.entity.QMeeting.meeting;
import static com.moimzang.domain.meeting.entity.QMeetingAgenda.meetingAgenda;
import static com.moimzang.domain.schedule.entity.QScheduleAgendaLink.scheduleAgendaLink;

import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ScheduleAgendaLinkRepositoryImpl implements ScheduleAgendaLinkRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 안건 내용뿐 아니라 어느 회의의 안건인지도 응답에 써야 해서 meeting까지 fetch join한다
    @Override
    public List<ScheduleAgendaLink> findByScheduleForAssistant(Long scheduleId) {
        return queryFactory.selectFrom(scheduleAgendaLink)
                .join(scheduleAgendaLink.meetingAgenda, meetingAgenda).fetchJoin()
                .join(meetingAgenda.meeting, meeting).fetchJoin()
                .where(scheduleAgendaLink.schedule.id.eq(scheduleId))
                .orderBy(meeting.meetingDate.desc(), meetingAgenda.orderNo.asc())
                .fetch();
    }
}
