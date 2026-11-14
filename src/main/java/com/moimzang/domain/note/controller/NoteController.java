package com.moimzang.domain.note.controller;

import com.moimzang.domain.note.dto.request.NoteCreateRequest;
import com.moimzang.domain.note.dto.request.NoteUpdateRequest;
import com.moimzang.domain.note.dto.response.NoteCreateResponse;
import com.moimzang.domain.note.dto.response.NoteListResponse;
import com.moimzang.domain.note.dto.response.NoteUpdateResponse;
import com.moimzang.domain.note.service.NoteService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteController implements NoteApi {

    private final NoteService noteService;

    // 일정 운영 공간에 메모를 작성한다
    @Override
    @PostMapping("/api/schedules/{scheduleId}/notes")
    public ResponseEntity<BaseResponse<NoteCreateResponse>> createNote(
            @PathVariable Long scheduleId,
            @Valid @RequestBody NoteCreateRequest request
    ) {
        NoteCreateResponse response = noteService.createNote(scheduleId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정의 메모 목록을 유형·검색어로 걸러 최신 작성순으로 조회한다
    @Override
    @GetMapping("/api/schedules/{scheduleId}/notes")
    public ResponseEntity<BaseResponse<NoteListResponse>> listNotes(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        NoteListResponse response = noteService.listNotes(scheduleId, type, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 메모를 부분 수정한다
    @Override
    @PatchMapping("/api/notes/{noteId}")
    public ResponseEntity<BaseResponse<NoteUpdateResponse>> updateNote(
            @PathVariable Long noteId,
            @Valid @RequestBody NoteUpdateRequest request
    ) {
        NoteUpdateResponse response = noteService.updateNote(noteId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 메모를 삭제한다
    @Override
    @DeleteMapping("/api/notes/{noteId}")
    public ResponseEntity<BaseResponse<Void>> deleteNote(@PathVariable Long noteId) {
        noteService.deleteNote(noteId);
        return ResponseEntity.ok(BaseResponse.success());
    }
}
