package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AttendanceStatusToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RoundAttendeeRepository roundAttendeeRepository;

    @InjectMocks
    private AttendanceStatusTool tool;

    @Test
    void execute_차수별_출석_미출석_인원을_집계한다() throws Exception {
        ScheduleRound first = round(1L, 1, "1차");
        ScheduleAttendee minjun = attendee(401L, "강민준", "광고홍보학과", "1140");
        ScheduleAttendee gildong = attendee(402L, "홍길동", "경영학과", "2250");
        given(roundAttendeeRepository.findByScheduleForAssistant(eq(1L), eq(10L), eq(null)))
                .willReturn(List.of(
                        record(first, minjun, true),
                        record(first, gildong, false)));

        JsonNode round = execute("{\"scheduleId\":10}").get("rounds").get(0);

        assertThat(round.get("roundNo").asInt()).isEqualTo(1);
        assertThat(round.get("label").asText()).isEqualTo("1차");
        assertThat(round.get("totalCount").asInt()).isEqualTo(2);
        assertThat(round.get("checkedInCount").asInt()).isEqualTo(1);
        assertThat(round.get("notCheckedInCount").asInt()).isEqualTo(1);
    }

    @Test
    void execute_참가자마다_차수별_출석_여부를_함께_내려준다() throws Exception {
        ScheduleRound first = round(1L, 1, "1차");
        ScheduleRound second = round(2L, 2, "2차");
        ScheduleAttendee minjun = attendee(401L, "강민준", "광고홍보학과", "1140");
        given(roundAttendeeRepository.findByScheduleForAssistant(eq(1L), eq(10L), eq(null)))
                .willReturn(List.of(
                        record(first, minjun, true),
                        record(second, minjun, false)));

        JsonNode result = execute("{\"scheduleId\":10}");
        JsonNode member = result.get("members").get(0);

        assertThat(result.get("memberCount").asInt()).isEqualTo(1);
        assertThat(member.get("attendeeId").asLong()).isEqualTo(401L);
        assertThat(member.get("roundCount").asInt()).isEqualTo(2);
        assertThat(member.get("checkedInCount").asInt()).isEqualTo(1);
        // "누가 2차에만 빠졌어?" 같은 질문은 차수별 수만으로는 답할 수 없다
        assertThat(member.get("rounds").get(0).get("checkedIn").asBoolean()).isTrue();
        assertThat(member.get("rounds").get(1).get("roundNo").asInt()).isEqualTo(2);
        assertThat(member.get("rounds").get(1).get("checkedIn").asBoolean()).isFalse();
        assertThat(member.get("rounds").get(1).get("checkedInAt").isNull()).isTrue();
    }

    @Test
    void execute_roundNo를_주면_그_차수만_조회한다() throws Exception {
        ScheduleRound second = round(2L, 2, "2차");
        given(roundAttendeeRepository.findByScheduleForAssistant(eq(1L), eq(10L), eq(2)))
                .willReturn(List.of(record(second, attendee(401L, "강민준", "광고홍보학과", "1140"), false)));

        JsonNode result = execute("{\"scheduleId\":10,\"roundNo\":2}");

        assertThat(result.get("roundNo").asInt()).isEqualTo(2);
        assertThat(result.get("rounds")).hasSize(1);
        assertThat(result.get("rounds").get(0).get("notCheckedInCount").asInt()).isEqualTo(1);
    }

    @Test
    void execute_출석_기록이_없으면_빈_결과를_돌려준다() throws Exception {
        given(roundAttendeeRepository.findByScheduleForAssistant(eq(1L), eq(10L), eq(null)))
                .willReturn(List.of());

        JsonNode result = execute("{\"scheduleId\":10}");

        // 0건은 에러가 아니다 — 아직 출석을 만들지 않은 일정일 수 있어 AI가 설명하게 둔다
        assertThat(result.get("rounds")).isEmpty();
        assertThat(result.get("members")).isEmpty();
        assertThat(result.get("memberCount").asInt()).isZero();
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

    private ScheduleRound round(long id, int roundNo, String label) {
        ScheduleRound round = BeanUtils.instantiateClass(ScheduleRound.class);
        ReflectionTestUtils.setField(round, "id", id);
        ReflectionTestUtils.setField(round, "roundNo", roundNo);
        ReflectionTestUtils.setField(round, "label", label);
        return round;
    }

    private ScheduleAttendee attendee(Long id, String name, String affiliation, String phoneLast4) {
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "id", id);
        ReflectionTestUtils.setField(attendee, "name", name);
        ReflectionTestUtils.setField(attendee, "affiliation", affiliation);
        ReflectionTestUtils.setField(attendee, "phoneLast4", phoneLast4);
        return attendee;
    }

    private RoundAttendee record(ScheduleRound round, ScheduleAttendee attendee, boolean checkedIn) {
        RoundAttendee record = BeanUtils.instantiateClass(RoundAttendee.class);
        ReflectionTestUtils.setField(record, "scheduleRound", round);
        ReflectionTestUtils.setField(record, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(record, "isCheckedIn", checkedIn);
        ReflectionTestUtils.setField(record, "checkedInAt", checkedIn ? LocalDateTime.of(2026, 11, 20, 18, 5) : null);
        return record;
    }
}
