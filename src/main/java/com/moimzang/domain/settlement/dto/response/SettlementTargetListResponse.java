package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementTargetListResponse {

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "정산 명단 전체 인원 수(페이지네이션용 totalCount 아님)", example = "5")
    private final long targetCount;

    @Schema(description = "1인 청구액(명단 전원 동일)", example = "20000")
    private final Integer billedAmount;

    @Schema(description = "정산 대상 목록")
    private final List<SettlementTargetResponse> targets;

    @Schema(description = "다음 페이지 cursor. 없으면 마지막 페이지", example = "MTc2MzA0MDAwMDAwMA.12")
    private final String nextCursor;
}
