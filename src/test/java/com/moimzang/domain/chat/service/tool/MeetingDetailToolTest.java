package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
class MeetingDetailToolTest {

    private static final String DISCUSSION =
            "기획파트 강민준 파트장이 당일 운영 총괄을 맡겠다고 했고, 홍보파트 홍길동 파트장은 티켓 판매를 맡기로 했다.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingAgendaRepository meetingAgendaRepository;

    @InjectMocks
    private MeetingDetailTool tool;

    @Test
    void execute_안건의_논의_내용과_결정_사항을_원문_그대로_내려준다() throws Exception {
        givenMeeting();
        given(meetingAgendaRepository.findOrderedAgendas(900L))
                .willReturn(List.of(agenda(901L, 1, "운영 담당 배정", DISCUSSION, "운영 총괄은 강민준",
                        AgendaStatus.COMPLETED)));

        JsonNode agenda = execute("{\"meetingId\":900}").get("agendas").get(0);

        // 답이 본문 텍스트 안에 있는 질문을 커버하려면 원문이 그대로 넘어가야 한다
        assertThat(agenda.get("discussionNote").asText()).isEqualTo(DISCUSSION);
        assertThat(agenda.get("decision").asText()).isEqualTo("운영 총괄은 강민준");
        assertThat(agenda.get("content").asText()).isEqualTo("운영 담당 배정");
        assertThat(agenda.get("orderNo").asInt()).isEqualTo(1);
        assertThat(agenda.get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void execute_회의_정보와_AI_요약본을_함께_내려준다() throws Exception {
        givenMeeting();

        JsonNode result = execute("{\"meetingId\":900}");

        assertThat(result.get("title").asText()).isEqualTo("3차 정기회의");
        assertThat(result.get("meetingDate").asText()).isEqualTo("2026-11-10");
        assertThat(result.get("categoryName").asText()).isEqualTo("정기회의");
        assertThat(result.get("attendees").asText()).isEqualTo("운영진 6명");
        assertThat(result.get("aiSummary").asText()).isEqualTo("가을공연 역할을 분담했다.");
    }

    @Test
    void execute_완료된_안건_수를_세서_내려준다() throws Exception {
        givenMeeting();
        given(meetingAgendaRepository.findOrderedAgendas(900L))
                .willReturn(List.of(
                        agenda(901L, 1, "운영 담당 배정", DISCUSSION, "운영 총괄은 강민준", AgendaStatus.COMPLETED),
                        agenda(902L, 2, "예산 확정", null, null, AgendaStatus.IN_PROGRESS)));

        JsonNode result = execute("{\"meetingId\":900}");

        assertThat(result.get("agendaCount").asInt()).isEqualTo(2);
        assertThat(result.get("doneAgendaCount").asInt()).isEqualTo(1);
        // 아직 논의 전인 안건은 빈 값 그대로 넘긴다 — 지어내지 않게 하기 위해서다
        assertThat(result.get("agendas").get(1).get("discussionNote").isNull()).isTrue();
    }

    @Test
    void execute_다른_기수의_회의면_ChatToolArgumentException을_던진다() {
        given(meetingRepository.findForAssistant(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> tool.execute(1L, objectMapper.readTree("{\"meetingId\":99}")))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("search_meetings");
    }

    @Test
    void execute_meetingId가_없으면_ChatToolArgumentException을_던진다() {
        assertThatThrownBy(() -> tool.execute(1L, objectMapper.createObjectNode()))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("필수");
    }

    private void givenMeeting() {
        given(meetingRepository.findForAssistant(eq(1L), eq(900L))).willReturn(Optional.of(meeting()));
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private Meeting meeting() {
        MeetingCategory category = BeanUtils.instantiateClass(MeetingCategory.class);
        ReflectionTestUtils.setField(category, "name", "정기회의");

        Meeting meeting = BeanUtils.instantiateClass(Meeting.class);
        ReflectionTestUtils.setField(meeting, "id", 900L);
        ReflectionTestUtils.setField(meeting, "meetingCategory", category);
        ReflectionTestUtils.setField(meeting, "title", "3차 정기회의");
        ReflectionTestUtils.setField(meeting, "meetingDate", LocalDate.of(2026, 11, 10));
        ReflectionTestUtils.setField(meeting, "attendees", "운영진 6명");
        ReflectionTestUtils.setField(meeting, "listSummary", "가을공연 역할 분담");
        ReflectionTestUtils.setField(meeting, "summaryContent", "가을공연 역할을 분담했다.");
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        return meeting;
    }

    private MeetingAgenda agenda(Long id, int orderNo, String content, String discussionNote, String decision,
                                  AgendaStatus status) {
        MeetingAgenda agenda = BeanUtils.instantiateClass(MeetingAgenda.class);
        ReflectionTestUtils.setField(agenda, "id", id);
        ReflectionTestUtils.setField(agenda, "orderNo", orderNo);
        ReflectionTestUtils.setField(agenda, "content", content);
        ReflectionTestUtils.setField(agenda, "discussionNote", discussionNote);
        ReflectionTestUtils.setField(agenda, "decision", decision);
        ReflectionTestUtils.setField(agenda, "status", status);
        return agenda;
    }
}
