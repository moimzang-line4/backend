package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleAttendeeRepository extends JpaRepository<ScheduleAttendee, Long> {

    // 새 차수 생성 시 기존 참가자 전원을 미출석으로 포함시키기 위한 조회
    List<ScheduleAttendee> findByScheduleId(Long scheduleId);
}
