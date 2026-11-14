package com.moimzang.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentConfirmRequest {

    @Schema(description = "입금 완료 여부(true면 입금 확인, false면 되돌리기)", example = "true")
    @NotNull
    private Boolean paid;
}
