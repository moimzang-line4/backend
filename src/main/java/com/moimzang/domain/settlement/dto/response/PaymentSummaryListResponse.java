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
public class PaymentSummaryListResponse {

    @Schema(description = "입금 현황 목록")
    private final List<PaymentSummaryResponse> payments;

    @Schema(description = "다음 페이지 cursor. 없으면 마지막 페이지", example = "MTc2MzA0MDAwMDAwMA.12")
    private final String nextCursor;
}
