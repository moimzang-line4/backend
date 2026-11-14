package com.moimzang.domain.settlement.dto.response;

import com.moimzang.domain.settlement.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentStatusResponse {

    @Schema(description = "참가자 ID(schedule_attendee.id)", example = "1")
    private final Long attendeeId;

    @Schema(description = "이름", example = "김모임")
    private final String name;

    @Schema(description = "입금 상태")
    private final PaymentStatus paymentStatus;

    @Schema(description = "입금 완료일시(되돌리면 null)")
    private final LocalDateTime paidAt;
}
