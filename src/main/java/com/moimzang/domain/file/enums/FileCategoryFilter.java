package com.moimzang.domain.file.enums;

// 목록·ZIP 조회용 카테고리 필터 — ALL은 DB 컬럼 값이 아니라서 FileCategory와 분리했다
public enum FileCategoryFilter {
    ALL,
    ACTIVITY_DOC,
    BUDGET_PLAN,
    PHOTO,
    LEDGER_FILE;

    public FileCategory toFileCategoryOrNull() {
        return this == ALL ? null : FileCategory.valueOf(name());
    }
}
