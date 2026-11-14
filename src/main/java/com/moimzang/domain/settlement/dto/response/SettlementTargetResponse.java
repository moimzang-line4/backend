package com.moimzang.domain.settlement.dto.response;

import com.moimzang.domain.settlement.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementTargetResponse {

    @Schema(description = "정산 대상 ID", example = "1")
    private final Long settlementTargetId;

    @Schema(description = "참가자 ID(schedule_attendee.id)", example = "1")
    private final Long attendeeId;

    @Schema(description = "이름", example = "김모임")
    private final String name;

    @Schema(description = "학과·소속", example = "컴퓨터공학과")
    private final String affiliation;

    @Schema(description = "전화번호 뒤 4자리", example = "1234")
    private final String phoneLast4;

    @Schema(description = "1인 청구액", example = "20000")
    private final Integer billedAmount;

    @Schema(description = "입금 상태")
    private final PaymentStatus paymentStatus;
}
