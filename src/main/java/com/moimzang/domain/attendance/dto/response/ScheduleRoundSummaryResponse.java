package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleRoundSummaryResponse {

    @Schema(description = "차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "차수 번호", example = "1")
    private final Integer roundNo;

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "영수증 등록 여부", example = "false")
    private final Boolean hasReceipt;
}
