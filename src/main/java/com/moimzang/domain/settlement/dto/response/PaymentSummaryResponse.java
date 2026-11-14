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
public class PaymentSummaryResponse {

    @Schema(description = "참가자 ID(schedule_attendee.id)", example = "1")
    private final Long attendeeId;

    @Schema(description = "이름", example = "김모임")
    private final String name;

    @Schema(description = "학과·소속", example = "컴퓨터공학과")
    private final String affiliation;

    @Schema(description = "전화번호 뒤 4자리", example = "1234")
    private final String phoneLast4;

    @Schema(description = "그 일정 내 모든 차수의 청구액 합산", example = "40000")
    private final Integer totalBilledAmount;

    @Schema(description = "입금 상태(모든 차수가 PAID여야 PAID)")
    private final PaymentStatus paymentStatus;
}
