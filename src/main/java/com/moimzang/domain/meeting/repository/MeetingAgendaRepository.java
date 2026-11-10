package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingAgendaRepository extends JpaRepository<MeetingAgenda, Long>, MeetingAgendaRepositoryCustom {

    // 회의 상세조회에 쓸 안건 목록을 노출 순서대로 조회한다
    List<MeetingAgenda> findByMeetingIdOrderByOrderNoAsc(Long meetingId);

    // 회의 삭제 전 해당 회의의 안건을 전부 지운다 — meeting_agenda.meeting_id FK가 RESTRICT라
    // Meeting보다 먼저 지워야 한다
    void deleteAllByMeetingId(Long meetingId);

    // 새 안건의 orderNo(마지막+1)를 계산하기 위해 가장 뒤 안건 하나만 조회한다
    Optional<MeetingAgenda> findTopByMeetingIdOrderByOrderNoDesc(Long meetingId);

    // 회의의 전체 안건 수 — "안건 1개만 남으면 삭제 불가" 검증용
    long countByMeetingId(Long meetingId);
}
