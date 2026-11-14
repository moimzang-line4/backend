package com.moimzang.domain.note.repository;

import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import java.time.LocalDateTime;
import java.util.List;

public interface OperationNoteRepositoryCustom {

    // 일정의 메모 목록을 유형·검색어 조건과 cursor로 조회한다(최신 작성순)
    List<OperationNote> search(Long scheduleId, NoteType type, String keyword,
                               LocalDateTime cursorCreatedAt, Long cursorId, int limit);

    // 위 목록과 같은 유형·검색어 조건의 전체 건수(메모장 화면의 '메모 목록 N')
    long countForList(Long scheduleId, NoteType type, String keyword);

    // 기수의 운영 메모를 검색어·일정·유형 조건으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<OperationNote> searchForAssistant(Long groupTermId, String keyword, Long scheduleId, NoteType type,
                                            int limit);

    // 위 조회의 전체 건수. limit에 잘린 경우 "몇 건 중 일부"인지 알리는 데 쓴다
    long countForAssistant(Long groupTermId, String keyword, Long scheduleId, NoteType type);
}
