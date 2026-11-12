package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import java.util.List;
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
class NoteSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OperationNoteRepository operationNoteRepository;

    @InjectMocks
    private NoteSearchTool tool;

    @Test
    void execute_메모와_그_메모가_달린_일정을_함께_내려준다() throws Exception {
        given(operationNoteRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of(note(600L, NoteType.TODO, "티켓 인쇄", "150장 인쇄 맡기기", "강민준", false)));

        JsonNode note = execute("{}").get("notes").get(0);

        assertThat(note.get("noteId").asLong()).isEqualTo(600L);
        assertThat(note.get("type").asText()).isEqualTo("TODO");
        assertThat(note.get("title").asText()).isEqualTo("티켓 인쇄");
        assertThat(note.get("content").asText()).isEqualTo("150장 인쇄 맡기기");
        assertThat(note.get("assignee").asText()).isEqualTo("강민준");
        assertThat(note.get("todoDone").asBoolean()).isFalse();
        // 어느 일정의 메모인지 알아야 "가을공연 때 뭐 하기로 했지"에 답할 수 있다
        assertThat(note.get("scheduleId").asLong()).isEqualTo(10L);
        assertThat(note.get("scheduleTitle").asText()).isEqualTo("가을 정기공연");
    }

    @Test
    void execute_일반_메모는_완료_여부가_비어_있다() throws Exception {
        given(operationNoteRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of(note(601L, NoteType.MEMO, "대관 확인", "대강당 예약 완료", null, null)));

        JsonNode note = execute("{}").get("notes").get(0);

        // 완료 여부는 할 일에만 쓰는 값이라 일반 메모에서는 null이다
        assertThat(note.get("todoDone").isNull()).isTrue();
        assertThat(note.get("assignee").isNull()).isTrue();
    }

    @Test
    void execute_검색_조건을_그대로_레포지토리에_넘기고_결과에도_싣는다() throws Exception {
        given(operationNoteRepository.searchForAssistant(eq(1L), eq("티켓"), eq(10L), anyInt()))
                .willReturn(List.of(note(600L, NoteType.TODO, "티켓 인쇄", "150장 인쇄 맡기기", "강민준", false)));

        JsonNode result = execute("{\"keyword\":\"티켓\",\"scheduleId\":10}");

        assertThat(result.get("keyword").asText()).isEqualTo("티켓");
        assertThat(result.get("scheduleId").asLong()).isEqualTo(10L);
        assertThat(result.get("matchedNoteCount").asLong()).isEqualTo(1);
    }

    @Test
    void execute_상한을_넘으면_잘라내고_전체_건수를_알려준다() throws Exception {
        List<OperationNote> many = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> note((long) i, NoteType.MEMO, "메모" + i, "내용", null, null))
                .toList();
        given(operationNoteRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt())).willReturn(many);
        given(operationNoteRepository.countForAssistant(eq(1L), isNull(), isNull())).willReturn(51L);

        JsonNode result = execute("{}");

        assertThat(result.get("notes")).hasSize(50);
        assertThat(result.get("truncated").asBoolean()).isTrue();
        assertThat(result.get("matchedNoteCount").asLong()).isEqualTo(51);
    }

    @Test
    void execute_메모가_없으면_빈_결과를_돌려준다() throws Exception {
        given(operationNoteRepository.searchForAssistant(eq(1L), isNull(), isNull(), anyInt()))
                .willReturn(List.of());

        JsonNode result = execute("{}");

        // 0건은 에러가 아니다 — AI가 "찾지 못했다"고 답하게 둔다
        assertThat(result.get("notes")).isEmpty();
        assertThat(result.get("matchedNoteCount").asLong()).isZero();
        assertThat(result.get("truncated").asBoolean()).isFalse();
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private OperationNote note(Long id, NoteType type, String title, String content, String assignee,
                                Boolean todoDone) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", 10L);
        ReflectionTestUtils.setField(schedule, "title", "가을 정기공연");

        OperationNote note = BeanUtils.instantiateClass(OperationNote.class);
        ReflectionTestUtils.setField(note, "id", id);
        ReflectionTestUtils.setField(note, "schedule", schedule);
        ReflectionTestUtils.setField(note, "type", type);
        ReflectionTestUtils.setField(note, "title", title);
        ReflectionTestUtils.setField(note, "content", content);
        ReflectionTestUtils.setField(note, "assignee", assignee);
        ReflectionTestUtils.setField(note, "isTodoDone", todoDone);
        return note;
    }
}
