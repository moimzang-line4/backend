package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import java.time.LocalDate;
import java.util.List;

public interface MeetingRepositoryCustom {

    // 기수의 회의 목록을 상태·카테고리·검색어 조건과 cursor로 조회한다
    List<Meeting> search(Long groupTermId, MeetingStatus status, Long categoryId, String keyword,
                          LocalDate cursorMeetingDate, Long cursorId, int limit);
}
