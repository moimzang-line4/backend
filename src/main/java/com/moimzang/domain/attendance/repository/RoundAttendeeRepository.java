package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundAttendeeRepository extends JpaRepository<RoundAttendee, Long> {
}
