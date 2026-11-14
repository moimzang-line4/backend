package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerExportResponse {

    @Schema(description = "생성된 장부 엑셀 다운로드 URL", example = "http://localhost:8080/uploads/ledger-exports/2026/11/3f9a.xlsx")
    private final String downloadUrl;

    @Schema(description = "사람이 읽는 파일명", example = "모임장_26기_장부_20261115.xlsx")
    private final String fileName;

    @Schema(description = "생성 시각", example = "2026-11-15T18:00:00")
    private final LocalDateTime generatedAt;
}
