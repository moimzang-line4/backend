package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import java.util.List;
import java.util.Map;

public interface MeetingAgendaRepositoryCustom {

    // 일정에 연결할 수 있는 안건을 keyword·cursor 조건으로 조회한다.
    // 후보는 어느 일정에도 속하지 않은 안건뿐이다 — 회의가 일정에 속하지 않았고, 일정-안건 연결도 없어야 한다
    List<MeetingAgenda> searchLinkable(Long groupTermId, String keyword,
                                        Long cursorMeetingId, Long cursorAgendaId, int limit);

    // 위 조건을 그대로 적용한 전체 후보 수 — 모달 상단의 "안건 N개" 표시용
    long countLinkable(Long groupTermId, String keyword);

    // 회의 ID 목록으로 안건 개수를 한 번에 집계한다(N+1 방지)
    Map<Long, Long> countByMeetingIds(List<Long> meetingIds);

    // 회의 ID 목록으로 완료된 안건 개수를 한 번에 집계한다 — 진행률의 분자(N+1 방지)
    Map<Long, Long> countCompletedByMeetingIds(List<Long> meetingIds);
}
