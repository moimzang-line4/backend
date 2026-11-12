package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundAttendeeRepository extends JpaRepository<RoundAttendee, Long>, RoundAttendeeRepositoryCustom {

    // 차수 출석 현황(API-029)의 progress.totalCount용 — 필터와 무관하게 그 차수 전체 참가자 수
    long countByScheduleRoundId(Long scheduleRoundId);

    // 차수 출석 현황(API-029)의 progress.checkedCount용
    long countByScheduleRoundIdAndIsCheckedIn(Long scheduleRoundId, Boolean isCheckedIn);
}
