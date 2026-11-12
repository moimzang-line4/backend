package com.moimzang.domain.settlement.entity;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 정산 대상 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "settlement_target")
public class SettlementTarget extends BaseEntity {

    // 정산 대상 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 대상 차수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_round_id", nullable = false)
    private ScheduleRound scheduleRound;

    // 대상 참가자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_attendee_id", nullable = false)
    private ScheduleAttendee scheduleAttendee;

    // 1인당 청구액(100원 단위 버림)
    @Column(name = "billed_amount", nullable = false)
    private Integer billedAmount;

    // 입금 상태(미입금/입금완료, 입금완료 후 잠금)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    // 입금 완료일시
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 영수증 금액 변경 시 1인 청구액을 다시 계산한 값으로 갱신한다
    public void updateBilledAmount(int billedAmount) {
        this.billedAmount = billedAmount;
    }
}
