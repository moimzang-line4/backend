package com.moimzang.domain.note.repository;

import com.moimzang.domain.note.entity.OperationNote;
import java.util.List;

public interface OperationNoteRepositoryCustom {

    // 기수의 운영 메모를 검색어·일정 조건으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<OperationNote> searchForAssistant(Long groupTermId, String keyword, Long scheduleId, int limit);

    // 위 조회의 전체 건수. limit에 잘린 경우 "몇 건 중 일부"인지 알리는 데 쓴다
    long countForAssistant(Long groupTermId, String keyword, Long scheduleId);
}
