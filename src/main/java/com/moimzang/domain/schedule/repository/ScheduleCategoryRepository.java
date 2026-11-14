package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.ScheduleCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCategoryRepository extends JpaRepository<ScheduleCategory, Long> {

    // 기수의 일정 카테고리 목록을 만든 순서대로 조회한다
    List<ScheduleCategory> findByGroupTermIdOrderByIdAsc(Long groupTermId);

    // 카테고리를 기수 조건과 함께 조회한다 — 다른 기수의 카테고리가 일정에 붙는 걸 막는다
    Optional<ScheduleCategory> findByIdAndGroupTermId(Long id, Long groupTermId);

    // 같은 기수에 같은 이름의 카테고리가 있는지 확인한다
    boolean existsByGroupTermIdAndName(Long groupTermId, String name);
}
