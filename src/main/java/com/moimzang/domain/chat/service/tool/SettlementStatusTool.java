package com.moimzang.domain.chat.service.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.JsonNode;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.ai.AiTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
                        + "여기서 말하는 청구 금액은 영수증 금액에서 회비 지원금을 뺀 뒤 참가자 수로 나눈 "
                        + "'부원에게 걷을 돈'이다. 가게에서 실제 결제한 총액은 이 도구가 아니라 "
                        + "get_schedule_detail에 있다. 차수별 소계는 roundSummaries에 들어 있으니 직접 더하지 않는다. "
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
        List<RoundSummary> roundSummaries = aggregateByRound(targets);

        // 아직 못 받은 돈. 납부한 차수는 빼고 더해야 '미입금 총액'이 사람 단위 금액의 합과 맞는다
        int totalUnpaidAmount = rows.stream().mapToInt(Row::unpaidAmount).sum();
        int totalBilledAmount = rows.stream().mapToInt(Row::totalBilledAmount).sum();
        long unpaidCount = rows.stream().filter(Row::isUnpaid).count();

        return new Result(
                scheduleId,
                roundNo,
                rows.size(),
                (int) unpaidCount,
                rows.size() - (int) unpaidCount,
                totalUnpaidAmount,
                totalBilledAmount - totalUnpaidAmount,
                totalBilledAmount,
                roundSummaries,
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

    // "1차 얼마, 2차 얼마"를 AI가 사람별 row를 더해 만들지 않도록 차수 소계를 미리 내려준다.
    // 조회 정렬이 (이름, 차수)라 차수 번호순을 보장하려면 TreeMap이 필요하다
    private List<RoundSummary> aggregateByRound(List<SettlementTarget> targets) {
        Map<Integer, RoundAccumulator> byRound = new TreeMap<>();
        for (SettlementTarget target : targets) {
            Integer roundNo = target.getScheduleRound().getRoundNo();
            byRound.computeIfAbsent(roundNo, RoundAccumulator::new).add(target);
        }
        return byRound.values().stream().map(RoundAccumulator::toSummary).toList();
    }

    private static final class RoundAccumulator {

        private final Integer roundNo;
        private int targetCount;
        private int billedAmount;
        private int unpaidAmount;

        private RoundAccumulator(Integer roundNo) {
            this.roundNo = roundNo;
        }

        private void add(SettlementTarget target) {
            targetCount++;
            billedAmount += target.getBilledAmount();
            if (target.getPaymentStatus() != PaymentStatus.PAID) {
                unpaidAmount += target.getBilledAmount();
            }
        }

        private RoundSummary toSummary() {
            return new RoundSummary(roundNo, targetCount, billedAmount, unpaidAmount);
        }
    }

    private static final class Accumulator {

        private final ScheduleAttendee attendee;
        private final List<RoundRow> rounds = new ArrayList<>();
        private int totalAmount;
        private int unpaidAmount;
        private boolean allPaid = true;

        private Accumulator(ScheduleAttendee attendee) {
            this.attendee = attendee;
        }

        private void add(SettlementTarget target) {
            boolean paid = target.getPaymentStatus() == PaymentStatus.PAID;
            totalAmount += target.getBilledAmount();
            if (!paid) {
                unpaidAmount += target.getBilledAmount();
            }
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
                    unpaidAmount,
                    allPaid ? PaymentStatus.PAID.name() : PaymentStatus.UNPAID.name(),
                    rounds);
        }
    }

    // totalUnpaidAmount(아직 못 받은 돈)와 totalBilledAmount(전체 청구액)를 둘 다 내려준다 —
    // 이름이 비슷해 헷갈리기 쉬운 값이라 AI가 직접 계산하지 않고 고를 수 있게 분리했다
    private record Result(Long scheduleId, Integer roundNo, int totalCount, int unpaidCount, int paidCount,
                          int totalUnpaidAmount, int totalPaidAmount, int totalBilledAmount,
                          List<RoundSummary> roundSummaries, List<Row> members) {}

    // 차수별 청구액·미납액 소계. billedAmount는 그 차수에서 부원들에게 걷기로 한 금액의 합이라
    // 영수증 금액(get_schedule_detail의 receiptAmount)과 다르다 — 차이가 회비 지원금이다
    public record RoundSummary(Integer roundNo, int targetCount, int billedAmount, int unpaidAmount) {}

    public record Row(Long attendeeId, String name, String affiliation, String phoneLast4,
                      int totalBilledAmount, int unpaidAmount, String paymentStatus, List<RoundRow> rounds) {

        // paymentStatus와 중복이라 AI에게 보낼 JSON에는 싣지 않는다
        @JsonIgnore
        public boolean isUnpaid() {
            return PaymentStatus.UNPAID.name().equals(paymentStatus);
        }
    }

    public record RoundRow(Integer roundNo, int billedAmount, String paymentStatus) {}
}
