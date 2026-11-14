package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, LedgerEntryRepositoryCustom {

    // 영수증 삭제(API-035)로 차수가 사라질 때, 그 차수를 가리키던 자동기록의 참조만 끊는다.
    // 장부 원본(금액·잔액)은 그대로 두고 schedule_round_id만 고아 상태로 전환한다(decisions #4)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LedgerEntry e set e.scheduleRound = null where e.scheduleRound.id = :scheduleRoundId")
    void clearScheduleRoundReference(@Param("scheduleRoundId") Long scheduleRoundId);

    // 일정 삭제 시 그 일정의 모든 차수를 가리키던 자동기록의 참조만 끊는다. 장부 금액·잔액은 그대로 둔다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LedgerEntry e set e.scheduleRound = null
            where e.scheduleRound.id in (select r.id from ScheduleRound r where r.schedule.id = :scheduleId)
            """)
    void clearScheduleReference(@Param("scheduleId") Long scheduleId);
}
