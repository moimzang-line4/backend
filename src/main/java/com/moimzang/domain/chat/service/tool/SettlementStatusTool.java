package com.moimzang.domain.chat.service.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.ai.AiTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 일정의 입금 현황 조회 도구.
// 합산·인원 수는 여기서 확정해 내려준다 — AI가 금액을 직접 더하게 두면 틀릴 수 있고,
// 이 결과는 '미입금자 조회' 카드와 그 카드의 입금 확인 버튼(attendeeId)으로 그대로 쓰이기 때문이다
@Component
@RequiredArgsConstructor
public class SettlementStatusTool implements ChatTool {

    public static final String NAME = "get_settlement_status";

    private final SettlementTargetRepository settlementTargetRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                NAME,
                "일정의 입금 현황을 조회해 미입금 총액·미입금 인원·입금 완료 인원과 함께 "
                        + "참가자별 청구 금액과 입금 상태 목록을 반환한다. "
                        + "scheduleId가 필요하므로 일정 이름만 아는 경우 search_schedules를 먼저 호출한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "scheduleId", Map.of(
                                        "type", "integer",
                                        "description", "search_schedules로 얻은 일정 ID"),
                                "roundNo", Map.of(
                                        "type", "integer",
                                        "description", "차수 번호. 사용자가 '2차'라고 하면 2. 생략하면 전체 차수 합산")),
                        "required", List.of("scheduleId")));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        Long scheduleId = ChatToolArgs.requiredLong(args, "scheduleId");
        Integer roundNo = ChatToolArgs.optionalInt(args, "roundNo");

        List<SettlementTarget> targets =
                settlementTargetRepository.findByScheduleForAssistant(scheduleId, roundNo);

        List<Row> rows = aggregateByAttendee(targets);

        int totalUnpaidAmount = rows.stream().filter(Row::isUnpaid).mapToInt(Row::totalBilledAmount).sum();
        int totalPaidAmount = rows.stream().filter(r -> !r.isUnpaid()).mapToInt(Row::totalBilledAmount).sum();
        long unpaidCount = rows.stream().filter(Row::isUnpaid).count();

        return new Result(
                scheduleId,
                roundNo,
                rows.size(),
                (int) unpaidCount,
                rows.size() - (int) unpaidCount,
                totalUnpaidAmount,
                totalPaidAmount,
                rows);
    }

    // 명세의 입금 현황 규칙: 여러 차수에 참여한 사람은 금액을 합산하고,
    // 모든 차수가 입금완료인 사람만 '입금완료'로 본다(한 차수라도 미납이면 미입금)
    private List<Row> aggregateByAttendee(List<SettlementTarget> targets) {
        Map<Long, Accumulator> byAttendee = new LinkedHashMap<>();
        for (SettlementTarget target : targets) {
            ScheduleAttendee attendee = target.getScheduleAttendee();
            byAttendee.computeIfAbsent(attendee.getId(), id -> new Accumulator(attendee))
                    .add(target);
        }
        return byAttendee.values().stream().map(Accumulator::toRow).toList();
    }

    private static final class Accumulator {

        private final ScheduleAttendee attendee;
        private final List<RoundRow> rounds = new ArrayList<>();
        private int totalAmount;
        private boolean allPaid = true;

        private Accumulator(ScheduleAttendee attendee) {
            this.attendee = attendee;
        }

        private void add(SettlementTarget target) {
            boolean paid = target.getPaymentStatus() == PaymentStatus.PAID;
            totalAmount += target.getBilledAmount();
            allPaid &= paid;
            rounds.add(new RoundRow(
                    target.getScheduleRound().getRoundNo(),
                    target.getBilledAmount(),
                    target.getPaymentStatus().name()));
        }

        private Row toRow() {
            return new Row(
                    attendee.getId(),
                    attendee.getName(),
                    attendee.getAffiliation(),
                    attendee.getPhoneLast4(),
                    totalAmount,
                    allPaid ? PaymentStatus.PAID.name() : PaymentStatus.UNPAID.name(),
                    rounds);
        }
    }

    private record Result(Long scheduleId, Integer roundNo, int totalCount, int unpaidCount, int paidCount,
                          int totalUnpaidAmount, int totalPaidAmount, List<Row> members) {}

    public record Row(Long attendeeId, String name, String affiliation, String phoneLast4,
                      int totalBilledAmount, String paymentStatus, List<RoundRow> rounds) {

        // paymentStatus와 중복이라 AI에게 보낼 JSON에는 싣지 않는다
        @JsonIgnore
        public boolean isUnpaid() {
            return PaymentStatus.UNPAID.name().equals(paymentStatus);
        }
    }

    public record RoundRow(Integer roundNo, int billedAmount, String paymentStatus) {}
}
