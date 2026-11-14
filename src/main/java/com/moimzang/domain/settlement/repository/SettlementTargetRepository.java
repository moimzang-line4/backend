package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementTargetRepository extends JpaRepository<SettlementTarget, Long>,
        SettlementTargetRepositoryCustom {

    // 영수증 조회·등록(API-033/034)의 isLocked, 423 판단용 — 그 차수에 입금 완료가 1명이라도 있는지
    boolean existsByScheduleRoundIdAndPaymentStatus(Long scheduleRoundId, PaymentStatus paymentStatus);

    // 영수증 금액 변경 시(API-034) 재계산 대상 조회용
    List<SettlementTarget> findByScheduleRoundId(Long scheduleRoundId);

    // 영수증 삭제(API-035)로 차수가 사라질 때 그 차수의 청구 내역을 함께 삭제한다
    void deleteByScheduleRoundId(Long scheduleRoundId);

    // 일정 삭제 시 그 일정의 모든 차수의 정산 대상을 지운다 — 차수·참가자보다 먼저 지워야 한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from SettlementTarget t
            where t.scheduleRound.id in (select r.id from ScheduleRound r where r.schedule.id = :scheduleId)
            """)
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);
}
