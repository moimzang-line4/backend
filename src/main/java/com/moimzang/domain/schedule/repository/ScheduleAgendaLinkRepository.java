package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleAgendaLinkRepository
        extends JpaRepository<ScheduleAgendaLink, Long>, ScheduleAgendaLinkRepositoryCustom {

    // 안건 1건을 지우기 전에 그 안건에 걸린 일정-안건 연결을 먼저 지운다 —
    // schedule_agenda_link.meeting_agenda_id FK가 RESTRICT라 안건보다 먼저 사라져야 한다
    void deleteAllByMeetingAgendaId(Long meetingAgendaId);

    // 회의를 통째로 지우기 전에 그 회의의 모든 안건에 걸린 연결을 지운다.
    // 파생 쿼리로 쓰면 이름이 meetingAgenda의 meeting을 두 번 타고 들어가 읽기 어려워 JPQL로 둔다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ScheduleAgendaLink l
            where l.meetingAgenda.id in (
                select a.id from MeetingAgenda a where a.meeting.id = :meetingId
            )
            """)
    void deleteAllByMeetingId(@Param("meetingId") Long meetingId);
}
