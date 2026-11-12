package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundAttendeeRepository extends JpaRepository<RoundAttendee, Long>, RoundAttendeeRepositoryCustom {

    // 차수 출석 현황(API-029)의 progress.totalCount용 — 필터와 무관하게 그 차수 전체 참가자 수
    long countByScheduleRoundId(Long scheduleRoundId);

    // 차수 출석 현황(API-029)의 progress.checkedCount용
    long countByScheduleRoundIdAndIsCheckedIn(Long scheduleRoundId, Boolean isCheckedIn);

    // 차수에서 참가자 제외(API-032) 대상 조회 — attendeeId는 round_attendee가 아니라
    // schedule_attendee의 id다
    Optional<RoundAttendee> findByScheduleRoundIdAndScheduleAttendeeId(Long scheduleRoundId, Long scheduleAttendeeId);
}
