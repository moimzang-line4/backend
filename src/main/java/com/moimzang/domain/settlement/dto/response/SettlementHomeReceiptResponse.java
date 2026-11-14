package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementHomeReceiptResponse {

    @Schema(description = "차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "가게명", example = "스타벅스 신촌점")
    private final String storeName;

    @Schema(description = "영수증 총액", example = "50000")
    private final Integer totalAmount;

    @Schema(description = "등록일시(schedule_round.updated_at으로 대체)")
    private final LocalDateTime registeredAt;

    @Schema(description = "그 차수의 정산 상태(\"정산 완료\" 또는 \"정산중\")", example = "정산중")
    private final String settlementStatus;
}
