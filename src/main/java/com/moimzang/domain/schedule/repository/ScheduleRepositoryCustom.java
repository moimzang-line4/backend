package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.Schedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepositoryCustom {

    // 기수의 일정을 제목 검색어·기간으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<Schedule> searchForAssistant(Long groupTermId, String keyword, LocalDate from, LocalDate to, int limit);

    // 일정 하나를 카테고리까지 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // 모델이 만들어낸 scheduleId가 다른 기수의 것일 수 있어 기수 조건을 함께 건다
    Optional<Schedule> findForAssistant(Long groupTermId, Long scheduleId);
}
