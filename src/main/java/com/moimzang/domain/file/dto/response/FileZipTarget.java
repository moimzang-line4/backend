package com.moimzang.domain.file.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 트랜잭션 안에서 Entity를 평면값으로 뽑아둔 ZIP 묶음 대상 — 스트리밍 단계에서 Lazy 초기화 문제를 피한다
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileZipTarget {

    private final String groupTermName;
    private final List<FileZipEntry> entries;
}
