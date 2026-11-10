package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingAgendaRepository extends JpaRepository<MeetingAgenda, Long>, MeetingAgendaRepositoryCustom {

    // 회의 상세조회에 쓸 안건 목록을 노출 순서대로 조회한다
    List<MeetingAgenda> findByMeetingIdOrderByOrderNoAsc(Long meetingId);

    // 회의 삭제 전 해당 회의의 안건을 전부 지운다 — meeting_agenda.meeting_id FK가 RESTRICT라
    // Meeting보다 먼저 지워야 한다
    void deleteAllByMeetingId(Long meetingId);
}
