package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import java.time.LocalDateTime;
import java.util.List;

public interface RoundAttendeeRepositoryCustom {

    // 차수 출석 현황 조회(API-029)용 — limit건(size+1)을 cursor 다음부터 createdAt 오름차순으로
    // 가져온다. scheduleAttendee를 fetch join해서 이름·소속·전화번호를 한 번에 로딩한다.
    // cursorId가 null이면 첫 페이지
    List<RoundAttendee> search(Long roundId, Boolean isCheckedIn, String keyword,
                                LocalDateTime cursorCreatedAt, Long cursorId, int limit);
}
