package com.moimzang.domain.file.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// ZIP 안에 들어갈 파일 1개 — storageKey로 원본을 다시 읽고 entryName으로 ZIP에 쓴다
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileZipEntry {

    private final String storageKey;
    private final String entryName;
}
