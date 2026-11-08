package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.ScheduleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleCategoryRepository extends JpaRepository<ScheduleCategory, Long> {
}
