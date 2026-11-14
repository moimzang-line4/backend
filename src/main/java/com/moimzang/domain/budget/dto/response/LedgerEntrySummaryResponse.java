package com.moimzang.domain.budget.dto.response;

import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.budget.enums.LedgerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntrySummaryResponse {

    @Schema(description = "장부 항목 ID", example = "1")
    private final Long entryId;

    @Schema(description = "항목 유형", example = "EXPENSE")
    private final LedgerType type;

    @Schema(description = "항목명", example = "간식비")
    private final String itemName;

    @Schema(description = "금액", example = "15000")
    private final Integer amount;

    @Schema(description = "거래 일자", example = "2026-11-15")
    private final LocalDate entryDate;

    @Schema(description = "출처(RECEIPT: 자동 기록, MANUAL: 직접 입력)")
    private final LedgerSource source;
}
