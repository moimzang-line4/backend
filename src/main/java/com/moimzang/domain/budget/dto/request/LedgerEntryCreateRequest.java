package com.moimzang.domain.budget.dto.request;

import com.moimzang.domain.budget.enums.LedgerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntryCreateRequest {

    @Schema(description = "항목 유형", example = "EXPENSE")
    @NotNull
    private LedgerType type;

    @Schema(description = "항목명", example = "간식비")
    @NotBlank
    @Size(max = 100)
    private String itemName;

    @Schema(description = "금액(양의 정수)", example = "15000")
    @NotNull
    @Positive
    private Integer amount;

    @Schema(description = "거래 일자", example = "2026-11-15")
    @NotNull
    private LocalDate entryDate;
}
