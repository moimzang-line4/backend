package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.settlement.enums.PaymentStatus;

// 입금 현황 조회(API-042) 집계 결과 한 명. totalBilledAmount는 그 사람의 일정 내 모든 차수
// billedAmount 합산이고, paymentStatus는 모든 차수가 PAID일 때만 PAID다
public record PaymentSummary(ScheduleAttendee attendee, int totalBilledAmount, PaymentStatus paymentStatus) {
}
