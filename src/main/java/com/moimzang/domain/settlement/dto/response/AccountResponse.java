package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountResponse {

    @Schema(description = "차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "은행명", example = "국민은행")
    private final String bankName;

    @Schema(description = "계좌번호", example = "123456-78-901234")
    private final String accountNumber;

    @Schema(description = "예금주", example = "김모임")
    private final String accountHolder;
}
