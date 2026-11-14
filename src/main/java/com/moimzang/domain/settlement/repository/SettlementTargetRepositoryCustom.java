package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatusFilter;
import java.time.LocalDateTime;
import java.util.List;

public interface SettlementTargetRepositoryCustom {

    // 일정의 정산 대상을 참가자·차수 정보와 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // roundNo가 null이면 그 일정의 모든 차수를 본다
    List<SettlementTarget> findByScheduleForAssistant(Long scheduleId, Integer roundNo);

    // 정산 명단 조회(API-037)용 — limit건(size+1)을 cursor 다음부터 createdAt 오름차순으로
    // 가져온다. scheduleAttendee를 fetch join해서 이름·소속·전화번호를 한 번에 로딩한다.
    // cursorId가 null이면 첫 페이지
    List<SettlementTarget> search(Long roundId, String keyword, LocalDateTime cursorCreatedAt, Long cursorId, int limit);

    // 입금 확인·되돌리기(API-040)·입금 명단 삭제(API-041)용 — 그 사람의 그 일정 내 모든 차수
    // settlement_target을 scheduleRound와 함께 가져온다(차수별 재계산에 totalAmount·
    // clubSubsidyAmount가 필요하다)
    List<SettlementTarget> findByScheduleAndAttendee(Long scheduleId, Long attendeeId);

    // 입금 현황 조회(API-042)용 — 사람 단위로 그 일정 내 모든 차수 billedAmount를 합산하고,
    // 모든 차수가 PAID인 사람만 PAID로 집계한다. limit건(size+1)을 cursor 다음부터
    // schedule_attendee.createdAt 오름차순으로 가져온다. cursorId가 null이면 첫 페이지
    List<PaymentSummary> searchPaymentSummaries(Long scheduleId, PaymentStatusFilter status, String keyword,
                                                 LocalDateTime cursorCreatedAt, Long cursorId, int limit);
}
