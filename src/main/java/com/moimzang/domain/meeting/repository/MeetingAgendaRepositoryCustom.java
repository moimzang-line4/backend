package com.moimzang.domain.meeting.repository;

import java.util.List;
import java.util.Map;

public interface MeetingAgendaRepositoryCustom {

    // 회의 ID 목록으로 안건 개수를 한 번에 집계한다(N+1 방지)
    Map<Long, Long> countByMeetingIds(List<Long> meetingIds);
}
