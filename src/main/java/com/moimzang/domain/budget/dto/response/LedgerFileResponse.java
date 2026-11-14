package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerFileResponse {

    @Schema(description = "장부 파일 ID", example = "1")
    private final Long fileId;

    @Schema(description = "장부 파일명", example = "2026_26기_장부.xlsx")
    private final String name;
}
