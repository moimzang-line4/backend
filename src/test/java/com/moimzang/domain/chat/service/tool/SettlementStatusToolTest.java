package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementStatusToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @InjectMocks
    private SettlementStatusTool tool;

    @Test
    void execute_여러_차수에_참여한_사람은_금액을_합산한다() throws Exception {
        ScheduleAttendee minjun = attendee(401L, "강민준", "광고홍보학과", "1140");
        given(settlementTargetRepository.findByScheduleForAssistant(eq(10L), eq(null)))
                .willReturn(List.of(
                        target(minjun, round(1, 1), 20000, PaymentStatus.UNPAID),
                        target(minjun, round(2, 2), 27000, PaymentStatus.UNPAID)));

        JsonNode result = execute("{\"scheduleId\":10}");

        assertThat(result.get("totalCount").asInt()).isEqualTo(1);
        assertThat(result.get("members").get(0).get("totalBilledAmount").asInt()).isEqualTo(47000);
        assertThat(result.get("totalUnpaidAmount").asInt()).isEqualTo(47000);
    }

    @Test
    void execute_모든_차수가_입금완료여야_PAID로_본다() throws Exception {
        ScheduleAttendee allPaid = attendee(401L, "강민준", "광고홍보학과", "1140");
        ScheduleAttendee partial = attendee(402L, "홍길동", "경영학과", "2250");
        given(settlementTargetRepository.findByScheduleForAssistant(eq(10L), eq(null)))
                .willReturn(List.of(
                        target(allPaid, round(1, 1), 20000, PaymentStatus.PAID),
                        target(allPaid, round(2, 2), 27000, PaymentStatus.PAID),
                        target(partial, round(1, 1), 20000, PaymentStatus.PAID),
                        target(partial, round(2, 2), 27000, PaymentStatus.UNPAID)));

        JsonNode result = execute("{\"scheduleId\":10}");

        JsonNode members = result.get("members");
        assertThat(members.get(0).get("paymentStatus").asText()).isEqualTo("PAID");
        // 한 차수라도 미납이면 전체는 UNPAID다
        assertThat(members.get(1).get("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(result.get("paidCount").asInt()).isEqualTo(1);
        assertThat(result.get("unpaidCount").asInt()).isEqualTo(1);
        // 미납자의 금액은 납부한 차수까지 포함해 합산된다
        assertThat(result.get("totalUnpaidAmount").asInt()).isEqualTo(47000);
        assertThat(result.get("totalPaidAmount").asInt()).isEqualTo(47000);
    }

    @Test
    void execute_roundNo를_주면_그_차수만_조회한다() throws Exception {
        ScheduleAttendee minjun = attendee(401L, "강민준", "광고홍보학과", "1140");
        given(settlementTargetRepository.findByScheduleForAssistant(eq(10L), eq(2)))
                .willReturn(List.of(target(minjun, round(2, 2), 27000, PaymentStatus.UNPAID)));

        JsonNode result = execute("{\"scheduleId\":10,\"roundNo\":2}");

        assertThat(result.get("roundNo").asInt()).isEqualTo(2);
        assertThat(result.get("totalUnpaidAmount").asInt()).isEqualTo(27000);
    }

    @Test
    void execute_정산_대상이_없으면_빈_결과를_돌려준다() throws Exception {
        given(settlementTargetRepository.findByScheduleForAssistant(eq(10L), eq(null)))
                .willReturn(List.of());

        JsonNode result = execute("{\"scheduleId\":10}");

        // 0건은 에러가 아니다 — AI가 "대상이 없다"고 답하게 둔다
        assertThat(result.get("totalCount").asInt()).isZero();
        assertThat(result.get("members")).isEmpty();
        assertThat(result.get("totalUnpaidAmount").asInt()).isZero();
    }

    @Test
    void execute_카드에_필요한_식별_정보를_모두_내려준다() throws Exception {
        ScheduleAttendee minjun = attendee(401L, "강민준", "광고홍보학과", "1140");
        given(settlementTargetRepository.findByScheduleForAssistant(eq(10L), eq(null)))
                .willReturn(List.of(target(minjun, round(1, 1), 47000, PaymentStatus.UNPAID)));

        JsonNode member = execute("{\"scheduleId\":10}").get("members").get(0);

        // 카드의 '입금 확인' 버튼이 attendeeId로 API를 호출하므로 반드시 포함돼야 한다
        assertThat(member.get("attendeeId").asLong()).isEqualTo(401L);
        assertThat(member.get("name").asText()).isEqualTo("강민준");
        assertThat(member.get("affiliation").asText()).isEqualTo("광고홍보학과");
        assertThat(member.get("phoneLast4").asText()).isEqualTo("1140");
        // paymentStatus와 중복되는 파생 필드는 싣지 않는다
        assertThat(member.has("unpaid")).isFalse();
    }

    @Test
    void execute_scheduleId가_없으면_ChatToolArgumentException을_던진다() {
        assertThatThrownBy(() -> tool.execute(1L, objectMapper.createObjectNode()))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("필수");
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private ScheduleAttendee attendee(Long id, String name, String affiliation, String phoneLast4) {
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "id", id);
        ReflectionTestUtils.setField(attendee, "name", name);
        ReflectionTestUtils.setField(attendee, "affiliation", affiliation);
        ReflectionTestUtils.setField(attendee, "phoneLast4", phoneLast4);
        return attendee;
    }

    private ScheduleRound round(long id, int roundNo) {
        ScheduleRound round = BeanUtils.instantiateClass(ScheduleRound.class);
        ReflectionTestUtils.setField(round, "id", id);
        ReflectionTestUtils.setField(round, "roundNo", roundNo);
        return round;
    }

    private SettlementTarget target(ScheduleAttendee attendee, ScheduleRound round, int amount, PaymentStatus status) {
        SettlementTarget target = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(target, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(target, "scheduleRound", round);
        ReflectionTestUtils.setField(target, "billedAmount", amount);
        ReflectionTestUtils.setField(target, "paymentStatus", status);
        return target;
    }
}
