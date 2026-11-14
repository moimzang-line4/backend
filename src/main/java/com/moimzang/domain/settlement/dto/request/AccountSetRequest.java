package com.moimzang.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountSetRequest {

    @Schema(description = "은행명", example = "국민은행")
    @NotBlank
    @Size(max = 50)
    private String bankName;

    @Schema(description = "계좌번호", example = "123456-78-901234")
    @NotBlank
    @Size(max = 50)
    private String accountNumber;

    @Schema(description = "예금주", example = "김모임")
    @NotBlank
    @Size(max = 50)
    private String accountHolder;
}
