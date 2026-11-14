package com.moimzang.domain.budget.repository;

import com.moimzang.domain.budget.entity.LedgerEntry;
import java.util.List;
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

    // 일정 삭제 시 그 일정을 가리키던 자동기록의 참조만 끊는다. 자동기록은 이제 schedule_id로
    // 연결되므로(일정 단위 합산) e.schedule을 직접 조건·대상으로 삼는다. e.scheduleRound 조건은
    // 과거 "차수 단위" 설계에서 생긴 레거시 데이터를 대비해 함께 둔다. 장부 금액·잔액은 그대로 둔다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LedgerEntry e set e.schedule = null, e.scheduleRound = null
            where e.schedule.id = :scheduleId
               or e.scheduleRound.id in (select r.id from ScheduleRound r where r.schedule.id = :scheduleId)
            """)
    void clearScheduleReference(@Param("scheduleId") Long scheduleId);

    // 정산 내역 새로고침(API-074) 대상 일정 선정용 — 정산 대상이 이미 전부 사라졌어도 자동기록이
    // 남아 있는 일정은 삭제 판단을 위해 대상에 포함돼야 한다. schedule이 null인 고아 자동기록은
    // e.schedule.id가 결코 비교 대상 termId 조건을 만족하지 않아 자연히 제외된다
    @Query("""
            select distinct e.schedule.id from LedgerEntry e
            where e.groupTerm.id = :groupTermId and e.autoCategory is not null and e.schedule is not null
            """)
    List<Long> findAutoScheduleIds(@Param("groupTermId") Long groupTermId);

    // 정산 내역 새로고침(API-074)이 한 일정의 기존 자동기록을 전부 가져와 upsert·삭제를 판단한다.
    // auto_category가 null인 직접 입력 내역은 조건에서 제외돼 절대 섞이지 않는다
    List<LedgerEntry> findByScheduleIdAndAutoCategoryIsNotNull(Long scheduleId);
}
