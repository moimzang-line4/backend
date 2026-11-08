package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleAttendeeRepository extends JpaRepository<ScheduleAttendee, Long> {
}
