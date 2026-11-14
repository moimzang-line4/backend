package com.moimzang.domain.note.service;

import com.moimzang.domain.note.dto.request.NoteCreateRequest;
import com.moimzang.domain.note.dto.request.NoteUpdateRequest;
import com.moimzang.domain.note.dto.response.NoteCreateResponse;
import com.moimzang.domain.note.dto.response.NoteUpdateResponse;
import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.exception.NoteErrorCode;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

    private final OperationNoteRepository operationNoteRepository;
    private final ScheduleRepository scheduleRepository;

    // 일정 운영 공간에 메모를 작성한다
    @Transactional
    public NoteCreateResponse createNote(Long scheduleId, NoteCreateRequest request) {
        log.info("[메모 작성] 작성을 시작합니다. scheduleId = {}, type = {}", scheduleId, request.getType());

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[메모 작성] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
                    return new CustomException(NoteErrorCode.NOTE_404_SCHEDULE_NOT_FOUND);
                });

        // 담당자는 선택 항목이라 공백만 들어오면 비어 있는 것으로 저장한다
        String assignee = request.getAssignee() == null ? null : request.getAssignee().strip();
        OperationNote note = operationNoteRepository.save(OperationNote.create(
                schedule, request.getType(), request.getTitle().strip(),
                assignee == null || assignee.isEmpty() ? null : assignee,
                request.getContent().strip()));

        log.info("[메모 작성] 작성을 완료했습니다. noteId = {}", note.getId());
        return NoteCreateResponse.builder()
                .noteId(note.getId())
                .build();
    }

    // 메모를 부분 수정한다
    @Transactional
    public NoteUpdateResponse updateNote(Long noteId, NoteUpdateRequest request) {
        log.info("[메모 수정] 수정을 시작합니다. noteId = {}, type = {}", noteId, request.getType());

        OperationNote note = findNote(noteId, "메모 수정");
        note.update(request.getType(), stripOrNull(request.getTitle()), stripOrNull(request.getAssignee()),
                stripOrNull(request.getContent()));

        log.info("[메모 수정] 수정을 완료했습니다. noteId = {}", noteId);
        return NoteUpdateResponse.builder()
                .noteId(note.getId())
                .build();
    }

    // 메모를 삭제한다
    @Transactional
    public void deleteNote(Long noteId) {
        log.info("[메모 삭제] 삭제를 시작합니다. noteId = {}", noteId);

        OperationNote note = findNote(noteId, "메모 삭제");
        operationNoteRepository.delete(note);

        log.info("[메모 삭제] 삭제를 완료했습니다. noteId = {}", noteId);
    }

    private OperationNote findNote(Long noteId, String action) {
        return operationNoteRepository.findById(noteId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 메모입니다. noteId = {}", action, noteId);
                    return new CustomException(NoteErrorCode.NOTE_404_NOT_FOUND);
                });
    }

    private String stripOrNull(String value) {
        return value == null ? null : value.strip();
    }
}
