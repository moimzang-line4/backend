package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.ScheduleCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCategoryRepository extends JpaRepository<ScheduleCategory, Long> {

    // 기수의 일정 카테고리 목록을 만든 순서대로 조회한다
    List<ScheduleCategory> findByGroupTermIdOrderByIdAsc(Long groupTermId);

    // 같은 기수에 같은 이름의 카테고리가 있는지 확인한다
    boolean existsByGroupTermIdAndName(Long groupTermId, String name);
}
