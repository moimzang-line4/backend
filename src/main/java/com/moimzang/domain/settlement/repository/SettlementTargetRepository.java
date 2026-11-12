package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementTargetRepository extends JpaRepository<SettlementTarget, Long>,
        SettlementTargetRepositoryCustom {

    // 영수증 조회·등록(API-033/034)의 isLocked, 423 판단용 — 그 차수에 입금 완료가 1명이라도 있는지
    boolean existsByScheduleRoundIdAndPaymentStatus(Long scheduleRoundId, PaymentStatus paymentStatus);

    // 영수증 금액 변경 시(API-034) 재계산 대상 조회용
    List<SettlementTarget> findByScheduleRoundId(Long scheduleRoundId);
}
