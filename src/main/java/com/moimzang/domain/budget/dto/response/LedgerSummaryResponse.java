package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerSummaryResponse {

    @Schema(description = "등록된 장부 파일(없으면 null)")
    private final LedgerFileResponse ledgerFile;

    @Schema(description = "현재 잔액(그룹 전체 누적, 기수가 바뀌어도 이월)", example = "1250000")
    private final Long currentBalance;

    @Schema(description = "누적 수입(그룹 전체)", example = "2000000")
    private final Long totalIncome;

    @Schema(description = "누적 지출(그룹 전체)", example = "750000")
    private final Long totalExpense;

    @Schema(description = "이 기수의 장부 내역")
    private final List<LedgerEntrySummaryResponse> entries;

    @Schema(description = "다음 페이지 cursor(마지막 페이지면 null)")
    private final String nextCursor;
}
