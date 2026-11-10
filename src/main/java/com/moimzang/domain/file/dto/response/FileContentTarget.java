package com.moimzang.domain.file.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 단일 파일 다운로드용 — Service→Controller 내부 전달용(JSON 아님)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileContentTarget {

    private final String name;
    private final String extension;
    private final String storageKey;
}
