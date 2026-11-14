package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntryOriginResponse {

    @Schema(description = "출처 일정 ID", example = "1")
    private final Long scheduleId;

    @Schema(description = "출처 일정 제목", example = "정기 모임")
    private final String title;
}
