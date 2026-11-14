package com.moimzang.domain.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerSyncResponse {

    @Schema(description = "새로 생성된 자동 기록 수", example = "3")
    private final int createdCount;

    @Schema(description = "금액·항목명·거래일 중 하나라도 바뀌어 갱신된 자동 기록 수(값이 같으면 포함하지 않음)", example = "1")
    private final int updatedCount;

    @Schema(description = "조건이 깨져(예: 입금 되돌리기) 삭제된 자동 기록 수", example = "0")
    private final int deletedCount;
}
