package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementNoticeResponse {

    @Schema(description = "일정 제목", example = "정기 모임")
    private final String scheduleTitle;

    @Schema(description = "선택한 차수별 표시명·1인 청구액")
    private final List<SettlementNoticeRoundResponse> rounds;

    @Schema(description = "선택 차수 1인 청구액 합", example = "35000")
    private final Integer combinedAmount;

    @Schema(description = "대표 계좌(선택 차수 중 roundNo가 가장 작은 차수의 계좌)")
    private final SettlementNoticeAccountResponse account;

    @Schema(description = "그대로 복사해 보낼 수 있는 공지 문구")
    private final String noticeText;
}
