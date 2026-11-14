package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementNoticeRoundResponse {

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "그 차수의 1인 청구액", example = "20000")
    private final Integer billedAmount;
}
