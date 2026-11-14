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
public class SettlementTargetImportResponse {

    @Schema(description = "그 차수의 출석 완료자 목록")
    private final List<SettlementImportAttendeeResponse> attendees;

    @Schema(description = "저장 시 적용될 1인 청구액(미리보기, 명단 전원 동일)", example = "20000")
    private final int expectedBilledAmount;
}
