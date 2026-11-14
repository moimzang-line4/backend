package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingAgendaRepository extends JpaRepository<MeetingAgenda, Long>, MeetingAgendaRepositoryCustom {

    // 회의 상세조회에 쓸 안건 목록을 노출 순서대로 조회한다
    @Query("""
            select a from MeetingAgenda a
            where a.meeting.id = :meetingId
            order by a.orderNo asc
            """)
    List<MeetingAgenda> findOrderedAgendas(@Param("meetingId") Long meetingId);

    // 회의 삭제 전 해당 회의의 안건을 전부 지운다 — meeting_agenda.meeting_id FK가 RESTRICT라
    // Meeting보다 먼저 지워야 한다
    void deleteAllByMeetingId(Long meetingId);

    // 일정 삭제 시 그 일정에서 만든 회의록의 안건을 전부 지운다 — 회의록보다 먼저 지워야 한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MeetingAgenda a
            where a.meeting.id in (select m.id from Meeting m where m.schedule.id = :scheduleId)
            """)
    void deleteAllByMeetingScheduleId(@Param("scheduleId") Long scheduleId);

    // 새 안건의 orderNo(마지막+1)를 계산하기 위해 가장 뒤 안건부터 내림차순으로 조회한다.
    // orderNo에 유니크 제약이 없어 동시 추가 시 중복이 생길 수 있는데, LIMIT으로 1건만
    // 받으면 중복이 있어도 예외 없이 그중 하나로 계속 진행한다(호출부에서 Pageable(0,1) 전달)
    @Query("""
            select a from MeetingAgenda a
            where a.meeting.id = :meetingId
            order by a.orderNo desc
            """)
    List<MeetingAgenda> findLastAgendas(@Param("meetingId") Long meetingId, Pageable pageable);

    // 회의의 전체 안건 수 — "안건 1개만 남으면 삭제 불가" 검증용
    long countByMeetingId(Long meetingId);

    // 회의 종료 응답의 doneAgendaCount 계산용 — 상태별 안건 수
    long countByMeetingIdAndStatus(Long meetingId, AgendaStatus status);
}
