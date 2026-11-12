package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReceiptRegisterResponse {

    @Schema(description = "차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "영수증 총액", example = "50000")
    private final Integer totalAmount;

    @Schema(description = "회비 지원금", example = "10000")
    private final Integer clubSubsidyAmount;
}
