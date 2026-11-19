package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRoundRepository extends JpaRepository<ScheduleRound, Long> {

    // 차수 목록 조회(API-027)용 — roundNo 오름차순
    @Query("""
            select r from ScheduleRound r
            where r.schedule.id = :scheduleId
            order by r.roundNo asc
            """)
    List<ScheduleRound> findOrderedRounds(@Param("scheduleId") Long scheduleId);

    // 새 차수의 roundNo(마지막+1)를 계산하기 위해 가장 뒤 차수부터 내림차순으로 조회한다.
    // 호출부에서 Pageable(0,1)을 넘겨 1건만 받는다(MeetingAgendaRepository.findLastAgendas와 동일 패턴)
    @Query("""
            select r from ScheduleRound r
            where r.schedule.id = :scheduleId
            order by r.roundNo desc
            """)
    List<ScheduleRound> findLastRounds(@Param("scheduleId") Long scheduleId, Pageable pageable);

    // 일정 삭제 시 그 일정의 차수를 지운다 — 출석·정산 대상·장부 참조를 먼저 정리한 뒤 호출한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScheduleRound r where r.schedule.id = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);

    // 정산 내역 새로고침(API-074) 대상 일정 선정용 — 영수증 지출/지원금 자동기록은 정산 명단과
    // 무관하게 영수증만으로 생기므로, 정산 대상이 아직 하나도 없는 일정도 놓치지 않아야 한다
    @Query("""
            select distinct r.schedule.id from ScheduleRound r
            where r.schedule.groupTerm.id = :groupTermId and r.totalAmount is not null
            """)
    List<Long> findScheduleIdsWithReceiptByGroupTerm(@Param("groupTermId") Long groupTermId);
}
