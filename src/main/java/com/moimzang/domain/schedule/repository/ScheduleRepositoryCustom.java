package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.Schedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepositoryCustom {

    // 캘린더용 — 기간과 겹치는 기수의 일정을 시작 시각 오름차순 cursor로 조회한다
    List<Schedule> searchCalendar(Long groupTermId, LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                  LocalDateTime cursorStartAt, Long cursorId, int limit);

    // 홈 '다가오는 운영 일정'용 — 운영 공간이 있고 아직 끝나지 않은 일정을 시작 시각순으로 조회한다
    List<Schedule> findUpcomingOperationSchedules(Long groupTermId, LocalDateTime now, int limit);

    // 기수의 일정을 제목 검색어·기간으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<Schedule> searchForAssistant(Long groupTermId, String keyword, LocalDate from, LocalDate to, int limit);

    // 일정 하나를 카테고리까지 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // 모델이 만들어낸 scheduleId가 다른 기수의 것일 수 있어 기수 조건을 함께 건다
    Optional<Schedule> findForAssistant(Long groupTermId, Long scheduleId);
}
