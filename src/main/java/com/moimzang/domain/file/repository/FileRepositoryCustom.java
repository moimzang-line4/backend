package com.moimzang.domain.file.repository;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import java.time.LocalDateTime;
import java.util.List;

public interface FileRepositoryCustom {

    // 목록 조회(cursor 페이지네이션)용 — limit건(size+1)을 cursor 다음부터 정렬 순서대로 가져온다.
    // cursorId가 null이면 첫 페이지. sort가 NAME이면 cursorName, 아니면 cursorCreatedAt을 쓴다
    List<File> search(Long groupTermId, FileCategory category, String keyword, Long scheduleId, FileSortOption sort,
                       LocalDateTime cursorCreatedAt, String cursorName, Long cursorId, int limit);

    // ZIP 전용 — 페이지네이션 없이 조건에 맞는 전체를 이름순으로 반환
    List<File> searchAll(Long groupTermId, FileCategory category, String keyword);
}
