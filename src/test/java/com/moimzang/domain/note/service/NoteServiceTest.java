package com.moimzang.domain.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.note.dto.request.NoteCreateRequest;
import com.moimzang.domain.note.dto.request.NoteUpdateRequest;
import com.moimzang.domain.note.dto.response.NoteCreateResponse;
import com.moimzang.domain.note.dto.response.NoteListResponse;
import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import com.moimzang.domain.note.exception.NoteErrorCode;
import com.moimzang.domain.note.mapper.NoteMapper;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private OperationNoteRepository operationNoteRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final NoteMapper noteMapper = Mappers.getMapper(NoteMapper.class);

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(operationNoteRepository, scheduleRepository, noteMapper);
    }

    @Test
    void listNotes_유형과_검색어를_조회_조건으로_넘기고_다음_페이지가_없으면_nextCursor는_null() {
        given(scheduleRepository.existsById(10L)).willReturn(true);
        OperationNote todo = note(NoteType.TODO, "포스터", "김가은", "인쇄");
        given(operationNoteRepository.search(eq(10L), eq(NoteType.TODO), eq("포스터"), isNull(), isNull(), eq(21)))
                .willReturn(List.of(todo));

        NoteListResponse response = noteService.listNotes(10L, "todo", "포스터", null, null);

        assertThat(response.getNotes()).hasSize(1);
        assertThat(response.getNotes().get(0).getNoteId()).isEqualTo(1L);
        assertThat(response.getNotes().get(0).getType()).isEqualTo(NoteType.TODO);
        assertThat(response.getNotes().get(0).getIsTodoDone()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listNotes_ALL이면_유형_조건_없이_조회한다() {
        given(scheduleRepository.existsById(10L)).willReturn(true);
        given(operationNoteRepository.search(eq(10L), isNull(), isNull(), isNull(), isNull(), eq(21)))
                .willReturn(List.of());

        NoteListResponse response = noteService.listNotes(10L, "ALL", null, null, null);

        assertThat(response.getNotes()).isEmpty();
    }

    @Test
    void listNotes_size보다_많으면_마지막_메모로_다음_cursor를_만든다() {
        given(scheduleRepository.existsById(10L)).willReturn(true);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 7, 10, 30);
        List<OperationNote> fetched = LongStream.rangeClosed(1, 3)
                .mapToObj(id -> {
                    OperationNote note = note(NoteType.GENERAL, "메모" + id, null, "내용");
                    ReflectionTestUtils.setField(note, "id", 10 - id);
                    ReflectionTestUtils.setField(note, "createdAt", createdAt);
                    return note;
                })
                .toList();
        given(operationNoteRepository.search(eq(10L), isNull(), isNull(), isNull(), isNull(), eq(3)))
                .willReturn(fetched);

        NoteListResponse response = noteService.listNotes(10L, null, null, null, 2);

        assertThat(response.getNotes()).extracting("noteId").containsExactly(9L, 8L);
        CursorCodec next = CursorCodec.decode(response.getNextCursor());
        assertThat(next.getId()).isEqualTo(8L);
        assertThat(LocalDateTime.parse(next.getSortValue())).isEqualTo(createdAt);
    }

    @Test
    void listNotes_cursor를_풀어서_다음_페이지를_조회한다() {
        given(scheduleRepository.existsById(10L)).willReturn(true);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 7, 10, 30);
        String cursor = CursorCodec.encode(createdAt.toString(), 8L);
        given(operationNoteRepository.search(eq(10L), isNull(), isNull(), eq(createdAt), eq(8L), eq(21)))
                .willReturn(List.of());

        NoteListResponse response = noteService.listNotes(10L, null, null, cursor, null);

        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listNotes_없는_일정이면_NOTE_404_SCHEDULE_NOT_FOUND() {
        given(scheduleRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> noteService.listNotes(99L, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(NoteErrorCode.NOTE_404_SCHEDULE_NOT_FOUND);
    }

    @Test
    void listNotes_잘못된_cursor면_NOTE_400_INVALID_CURSOR() {
        given(scheduleRepository.existsById(10L)).willReturn(true);

        assertThatThrownBy(() -> noteService.listNotes(10L, null, null, "깨진cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(NoteErrorCode.NOTE_400_INVALID_CURSOR);
    }

    @Test
    void listNotes_지원하지_않는_유형이면_COMMON_400() {
        given(scheduleRepository.existsById(10L)).willReturn(true);

        assertThatThrownBy(() -> noteService.listNotes(10L, "MEMO", null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void createNote_할_일은_미완료로_저장한다() {
        Schedule schedule = mock(Schedule.class);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(operationNoteRepository.save(any(OperationNote.class))).willAnswer(invocation -> {
            OperationNote saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        NoteCreateResponse response = noteService.createNote(10L, NoteCreateRequest.builder()
                .type(NoteType.TODO)
                .title(" 공연 포스터 인쇄 ")
                .assignee("김가은")
                .content("A2 30장")
                .build());

        OperationNote saved = captureSaved();
        assertThat(response.getNoteId()).isEqualTo(1L);
        assertThat(saved.getSchedule()).isSameAs(schedule);
        assertThat(saved.getType()).isEqualTo(NoteType.TODO);
        assertThat(saved.getTitle()).isEqualTo("공연 포스터 인쇄");
        assertThat(saved.getIsTodoDone()).isFalse();
    }

    @Test
    void createNote_할_일이_아니면_완료_여부를_비우고_공백_담당자는_저장하지_않는다() {
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(mock(Schedule.class)));
        given(operationNoteRepository.save(any(OperationNote.class))).willAnswer(invocation -> invocation.getArgument(0));

        noteService.createNote(10L, NoteCreateRequest.builder()
                .type(NoteType.GENERAL)
                .title("대관 확인")
                .assignee("   ")
                .content("대강당 예약 완료")
                .build());

        OperationNote saved = captureSaved();
        assertThat(saved.getIsTodoDone()).isNull();
        assertThat(saved.getAssignee()).isNull();
    }

    @Test
    void createNote_없는_일정이면_NOTE_404_SCHEDULE_NOT_FOUND() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.createNote(99L, NoteCreateRequest.builder()
                .type(NoteType.GENERAL).title("제목").content("내용").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(NoteErrorCode.NOTE_404_SCHEDULE_NOT_FOUND);
        verify(operationNoteRepository, never()).save(any());
    }

    @Test
    void updateNote_보낸_필드만_수정한다() {
        OperationNote note = note(NoteType.GENERAL, "대관 확인", "김가은", "대강당 예약");
        given(operationNoteRepository.findById(1L)).willReturn(Optional.of(note));

        noteService.updateNote(1L, NoteUpdateRequest.builder().content(" 소강당으로 변경 ").build());

        assertThat(note.getTitle()).isEqualTo("대관 확인");
        assertThat(note.getAssignee()).isEqualTo("김가은");
        assertThat(note.getContent()).isEqualTo("소강당으로 변경");
        assertThat(note.getType()).isEqualTo(NoteType.GENERAL);
    }

    @Test
    void updateNote_빈_담당자를_보내면_담당자를_지운다() {
        OperationNote note = note(NoteType.GENERAL, "대관 확인", "김가은", "대강당 예약");
        given(operationNoteRepository.findById(1L)).willReturn(Optional.of(note));

        noteService.updateNote(1L, NoteUpdateRequest.builder().assignee("").build());

        assertThat(note.getAssignee()).isNull();
    }

    @Test
    void updateNote_할_일로_바꾸면_미완료가_되고_다른_유형으로_바꾸면_완료_여부를_비운다() {
        OperationNote note = note(NoteType.GENERAL, "포스터", null, "인쇄");
        given(operationNoteRepository.findById(1L)).willReturn(Optional.of(note));

        noteService.updateNote(1L, NoteUpdateRequest.builder().type(NoteType.TODO).build());
        assertThat(note.getIsTodoDone()).isFalse();

        noteService.updateNote(1L, NoteUpdateRequest.builder().type(NoteType.DECISION).build());
        assertThat(note.getType()).isEqualTo(NoteType.DECISION);
        assertThat(note.getIsTodoDone()).isNull();
    }

    @Test
    void updateNote_할_일을_할_일로_다시_보내면_완료_여부를_유지한다() {
        OperationNote note = note(NoteType.TODO, "포스터", null, "인쇄");
        ReflectionTestUtils.setField(note, "isTodoDone", true);
        given(operationNoteRepository.findById(1L)).willReturn(Optional.of(note));

        noteService.updateNote(1L, NoteUpdateRequest.builder().type(NoteType.TODO).title("포스터 수정").build());

        assertThat(note.getIsTodoDone()).isTrue();
    }

    @Test
    void updateNote_없는_메모면_NOTE_404_NOT_FOUND() {
        given(operationNoteRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.updateNote(99L, NoteUpdateRequest.builder().title("제목").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(NoteErrorCode.NOTE_404_NOT_FOUND);
    }

    @Test
    void deleteNote_메모를_삭제한다() {
        OperationNote note = note(NoteType.GENERAL, "대관 확인", null, "대강당 예약");
        given(operationNoteRepository.findById(1L)).willReturn(Optional.of(note));

        noteService.deleteNote(1L);

        verify(operationNoteRepository).delete(note);
    }

    @Test
    void deleteNote_없는_메모면_NOTE_404_NOT_FOUND() {
        given(operationNoteRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(NoteErrorCode.NOTE_404_NOT_FOUND);
        verify(operationNoteRepository, never()).delete(any());
    }

    private OperationNote captureSaved() {
        ArgumentCaptor<OperationNote> captor = ArgumentCaptor.forClass(OperationNote.class);
        verify(operationNoteRepository).save(captor.capture());
        return captor.getValue();
    }

    private OperationNote note(NoteType type, String title, String assignee, String content) {
        OperationNote note = OperationNote.create(mock(Schedule.class), type, title, assignee, content);
        ReflectionTestUtils.setField(note, "id", 1L);
        return note;
    }
}
