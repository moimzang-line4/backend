package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
