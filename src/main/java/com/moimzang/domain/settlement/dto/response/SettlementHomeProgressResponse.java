package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementHomeProgressResponse {

    @Schema(description = "입금 완료 인원(그 사람의 일정 내 모든 차수가 PAID여야 포함)", example = "4")
    private final long paidCount;

    @Schema(description = "일정 내 정산 대상 전체 인원(중복 제거)", example = "10")
    private final long totalCount;

    @Schema(description = "정수 퍼센트(0~100). 전체 인원이 0명이면 0", example = "40")
    private final int rate;
}
