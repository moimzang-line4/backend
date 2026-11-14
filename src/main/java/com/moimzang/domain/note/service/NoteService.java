package com.moimzang.domain.note.service;

import com.moimzang.domain.note.dto.request.NoteCreateRequest;
import com.moimzang.domain.note.dto.request.NoteUpdateRequest;
import com.moimzang.domain.note.dto.response.NoteCreateResponse;
import com.moimzang.domain.note.dto.response.NoteListResponse;
import com.moimzang.domain.note.dto.response.NoteUpdateResponse;
import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import com.moimzang.domain.note.exception.NoteErrorCode;
import com.moimzang.domain.note.mapper.NoteMapper;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
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
    private final NoteMapper noteMapper;

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

    // 일정의 메모 목록을 유형·검색어로 걸러 최신 작성순으로 조회한다
    public NoteListResponse listNotes(Long scheduleId, String type, String keyword, String cursor, Integer size) {
        log.info("[메모 목록 조회] 조회를 시작합니다. scheduleId = {}, type = {}, keyword = {}, cursor = {}",
                scheduleId, type, keyword, cursor);

        if (!scheduleRepository.existsById(scheduleId)) {
            log.warn("[메모 목록 조회] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
            throw new CustomException(NoteErrorCode.NOTE_404_SCHEDULE_NOT_FOUND);
        }

        NoteType noteType = parseType(type);
        int limit = CursorPageConstants.resolveSize(size);

        // cursor의 정렬 값은 작성 시각을 ISO-8601 문자열로 직렬화한 것이다
        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorCreatedAt = LocalDateTime.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[메모 목록 조회] cursor 형식이 올바르지 않습니다. scheduleId = {}, cursor = {}", scheduleId, cursor);
                throw new CustomException(NoteErrorCode.NOTE_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 마지막 1건이 있으면 다음 페이지가 있다는 뜻이다
        List<OperationNote> fetched = operationNoteRepository.search(
                scheduleId, noteType, keyword, cursorCreatedAt, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<OperationNote> page = hasNext ? fetched.subList(0, limit) : fetched;

        String nextCursor = null;
        if (hasNext) {
            OperationNote last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt().toString(), last.getId());
        }

        log.info("[메모 목록 조회] 조회를 완료했습니다. scheduleId = {}, count = {}, hasNext = {}",
                scheduleId, page.size(), hasNext);
        return NoteListResponse.builder()
                .notes(noteMapper.toResponses(page))
                .nextCursor(nextCursor)
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

    // 유형을 생략하거나 ALL이면 전체 유형을 조회한다
    private NoteType parseType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return null;
        }
        try {
            return NoteType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[메모 목록 조회] 지원하지 않는 메모 유형입니다. type = {}", type);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }
    }

    private String stripOrNull(String value) {
        return value == null ? null : value.strip();
    }
}
