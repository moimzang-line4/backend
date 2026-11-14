package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntryCreateResponse {

    @Schema(description = "장부 항목 ID", example = "1")
    private final Long entryId;
}
