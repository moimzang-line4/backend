package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.Schedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long>, ScheduleRepositoryCustom {

    // 일정을 기수 조건과 함께 조회한다 — 다른 기수의 일정 ID를 보내 기수 경계를 넘는 걸 막는다
    Optional<Schedule> findByIdAndGroupTermId(Long id, Long groupTermId);
}
