package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementTargetSaveResponse {

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "저장된 정산 명단 인원 수", example = "5")
    private final long targetCount;

    @Schema(description = "1인 청구액(명단 전원 동일)", example = "20000")
    private final int billedAmount;
}
