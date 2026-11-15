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
    // 실제로 내보낼 바이트의 확장자. 썸네일은 원본과 다를 수 있어(gif 원본 → png 썸네일)
    // 원본 확장자를 그대로 쓰면 Content-Type이 어긋난다
    private final String extension;
    private final String storageKey;
}
