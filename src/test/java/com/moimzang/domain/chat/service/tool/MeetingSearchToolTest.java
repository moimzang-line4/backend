package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
class MeetingSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingAgendaRepository meetingAgendaRepository;

    @InjectMocks
    private MeetingSearchTool tool;

    @Test
    void execute_회의_정보와_안건_진행률을_함께_내려준다() throws Exception {
        given(meetingRepository.search(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(meeting(900L, "3차 정기회의", LocalDate.of(2026, 11, 10))));
        given(meetingAgendaRepository.countByMeetingIds(List.of(900L))).willReturn(Map.of(900L, 4L));
        given(meetingAgendaRepository.countCompletedByMeetingIds(List.of(900L))).willReturn(Map.of(900L, 3L));

        JsonNode row = execute("{}").get("meetings").get(0);

        assertThat(row.get("meetingId").asLong()).isEqualTo(900L);
        assertThat(row.get("title").asText()).isEqualTo("3차 정기회의");
        assertThat(row.get("meetingDate").asText()).isEqualTo("2026-11-10");
        assertThat(row.get("categoryName").asText()).isEqualTo("정기회의");
        assertThat(row.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(row.get("attendees").asText()).isEqualTo("운영진 6명");
        assertThat(row.get("agendaCount").asLong()).isEqualTo(4);
        assertThat(row.get("doneAgendaCount").asLong()).isEqualTo(3);
        // 진행률은 회의 상세 화면과 같은 공식이다
        assertThat(row.get("progressPercentage").asInt()).isEqualTo(75);
    }

    @Test
    void execute_안건이_하나도_없는_회의는_진행률이_0이다() throws Exception {
        given(meetingRepository.search(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(meeting(900L, "4차 정기회의", LocalDate.of(2026, 11, 12))));

        JsonNode row = execute("{}").get("meetings").get(0);

        // group by 결과에 행이 없는 회의는 0으로 채워야 한다
        assertThat(row.get("agendaCount").asLong()).isZero();
        assertThat(row.get("progressPercentage").asInt()).isZero();
    }

    @Test
    void execute_검색_결과가_0건이면_등록된_회의_제목을_함께_돌려준다() throws Exception {
        given(meetingRepository.search(eq(1L), isNull(), isNull(), eq("운영회의"), isNull(), isNull(), anyInt()))
                .willReturn(List.of());
        given(meetingRepository.search(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(meeting(900L, "3차 정기회의", LocalDate.of(2026, 11, 10))));

        JsonNode result = execute("{\"keyword\":\"운영회의\"}");

        // 줄여 부른 이름은 부분 일치로 안 걸린다 — 실제 제목을 보여줘야 "없다"고 단정하지 않는다
        assertThat(result.get("count").asInt()).isZero();
        assertThat(result.get("availableTitles").get(0).asText()).isEqualTo("3차 정기회의");
    }

    @Test
    void execute_검색_결과가_있으면_availableTitles를_싣지_않는다() throws Exception {
        given(meetingRepository.search(eq(1L), isNull(), isNull(), eq("정기"), isNull(), isNull(), anyInt()))
                .willReturn(List.of(meeting(900L, "3차 정기회의", LocalDate.of(2026, 11, 10))));

        JsonNode result = execute("{\"keyword\":\"정기\"}");

        assertThat(result.has("availableTitles")).isFalse();
    }

    @Test
    void execute_상한을_넘으면_잘라내고_truncated를_표시한다() throws Exception {
        List<Meeting> many = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> meeting((long) i, i + "차 회의", LocalDate.of(2026, 11, 10)))
                .toList();
        given(meetingRepository.search(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .willReturn(many);
        given(meetingAgendaRepository.countByMeetingIds(any())).willReturn(Map.of());
        given(meetingAgendaRepository.countCompletedByMeetingIds(any())).willReturn(Map.of());

        JsonNode result = execute("{}");

        assertThat(result.get("meetings")).hasSize(50);
        assertThat(result.get("truncated").asBoolean()).isTrue();
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private Meeting meeting(Long id, String title, LocalDate meetingDate) {
        MeetingCategory category = BeanUtils.instantiateClass(MeetingCategory.class);
        ReflectionTestUtils.setField(category, "id", 9L);
        ReflectionTestUtils.setField(category, "name", "정기회의");

        Meeting meeting = BeanUtils.instantiateClass(Meeting.class);
        ReflectionTestUtils.setField(meeting, "id", id);
        ReflectionTestUtils.setField(meeting, "meetingCategory", category);
        ReflectionTestUtils.setField(meeting, "title", title);
        ReflectionTestUtils.setField(meeting, "meetingDate", meetingDate);
        ReflectionTestUtils.setField(meeting, "attendees", "운영진 6명");
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        return meeting;
    }
}
