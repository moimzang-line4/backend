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
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "settlement_target", uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_target_round_attendee",
        columnNames = {"schedule_round_id", "schedule_attendee_id"}))
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

    // 정산 명단 저장(API-038)으로 새로 추가되는 참가자를 미입금 상태로 만든다
    public static SettlementTarget create(ScheduleRound scheduleRound, ScheduleAttendee scheduleAttendee, int billedAmount) {
        return SettlementTarget.builder()
                .scheduleRound(scheduleRound)
                .scheduleAttendee(scheduleAttendee)
                .billedAmount(billedAmount)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();
    }

    // 영수증 금액 변경 시 1인 청구액을 다시 계산한 값으로 갱신한다
    public void updateBilledAmount(int billedAmount) {
        this.billedAmount = billedAmount;
    }

    // 입금 상태를 지정값으로 바꾼다. 이미 같은 상태면 멱등하게 아무 것도 바꾸지 않고(기존
    // paidAt 유지), 실제로 상태가 바뀔 때만 paidAt을 갱신한다(입금 확인 시 현재 시각,
    // 되돌리기 시 null) — RoundAttendee.updateCheckedIn과 같은 패턴
    public void updatePaymentStatus(boolean paid) {
        PaymentStatus newStatus = paid ? PaymentStatus.PAID : PaymentStatus.UNPAID;
        if (this.paymentStatus == newStatus) {
            return;
        }
        this.paymentStatus = newStatus;
        this.paidAt = paid ? LocalDateTime.now() : null;
    }
}
