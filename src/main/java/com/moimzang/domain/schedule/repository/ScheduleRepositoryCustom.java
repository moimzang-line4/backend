package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.Schedule;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleRepositoryCustom {

    // 기수의 일정을 제목 검색어·기간으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<Schedule> searchForAssistant(Long groupTermId, String keyword, LocalDate from, LocalDate to, int limit);
}
