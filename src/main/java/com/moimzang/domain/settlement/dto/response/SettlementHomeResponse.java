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
public class SettlementHomeResponse {

    @Schema(description = "일정 제목", example = "정기 모임")
    private final String scheduleTitle;

    @Schema(description = "일정 전체 정산 상태(\"정산 완료\" 또는 \"정산중\")", example = "정산중")
    private final String status;

    @Schema(description = "사람 수 기준 입금 진행률")
    private final SettlementHomeProgressResponse progress;

    @Schema(description = "영수증이 등록된 차수 목록")
    private final List<SettlementHomeReceiptResponse> receipts;

    @Schema(description = "대표 계좌(계좌가 설정된 차수 중 roundNo가 가장 작은 차수). 설정된 계좌가 하나도 없으면 null")
    private final SettlementNoticeAccountResponse account;
}
