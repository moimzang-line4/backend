package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingRepositoryCustom {

    // 기수의 회의 목록을 상태·카테고리·검색어 조건과 cursor로 조회한다
    List<Meeting> search(Long groupTermId, MeetingStatus status, Long categoryId, String keyword,
                          LocalDate cursorMeetingDate, Long cursorId, int limit);

    // 회의 하나를 카테고리까지 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // 모델이 만들어낸 meetingId가 다른 기수의 것일 수 있어 기수 조건을 함께 건다
    Optional<Meeting> findForAssistant(Long groupTermId, Long meetingId);
}
