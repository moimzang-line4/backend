package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoundAttendeeRepository extends JpaRepository<RoundAttendee, Long>, RoundAttendeeRepositoryCustom {

    // 차수 출석 현황(API-029)의 progress.totalCount용 — 필터와 무관하게 그 차수 전체 참가자 수
    long countByScheduleRoundId(Long scheduleRoundId);

    // 차수 출석 현황(API-029)의 progress.checkedCount용.
    // 파생 쿼리로 두면 Spring Data가 메서드명 끝의 In을 IN 절 키워드로 읽어 'isChecked' 속성을
    // 찾다가 빈 생성에 실패한다 — 이름을 줄이고 JPQL로 고정한다
    @Query("""
            select count(r) from RoundAttendee r
            where r.scheduleRound.id = :scheduleRoundId
              and r.isCheckedIn = true
            """)
    long countCheckedIn(@Param("scheduleRoundId") Long scheduleRoundId);

    // 차수에서 참가자 제외(API-032) 대상 조회 — attendeeId는 round_attendee가 아니라
    // schedule_attendee의 id다
    Optional<RoundAttendee> findByScheduleRoundIdAndScheduleAttendeeId(Long scheduleRoundId, Long scheduleAttendeeId);

    // 영수증 삭제(API-035)로 차수가 사라질 때 그 차수의 출석 기록을 함께 삭제한다
    void deleteByScheduleRoundId(Long scheduleRoundId);

    // 일정 삭제 시 그 일정의 모든 차수의 출석 기록을 지운다 — 차수·참가자보다 먼저 지워야 한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RoundAttendee ra
            where ra.scheduleRound.id in (select r.id from ScheduleRound r where r.schedule.id = :scheduleId)
            """)
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);

    // 명단방 삭제 시 그 명단방 부원으로 등록된 참가자의 출석 기록을 지운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RoundAttendee ra
            where ra.scheduleAttendee.id in (
                select a.id from ScheduleAttendee a where a.member.memberList.id = :memberListId
            )
            """)
    void deleteAllByMemberListId(@Param("memberListId") Long memberListId);
}
