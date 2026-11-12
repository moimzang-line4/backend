package com.moimzang.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReceiptResponse {

    @Schema(description = "차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "차수 표시명", example = "1차")
    private final String label;

    @Schema(description = "가게명", example = "스타벅스 신촌점")
    private final String storeName;

    @Schema(description = "거래일시")
    private final LocalDateTime transactedAt;

    @Schema(description = "영수증 총액", example = "50000")
    private final Integer totalAmount;

    @Schema(description = "회비 지원금", example = "10000")
    private final Integer clubSubsidyAmount;

    @Schema(description = "영수증 이미지 URL")
    private final String imageUrl;

    @Schema(description = "등록일시(schedule_round.updated_at으로 대체)")
    private final LocalDateTime registeredAt;

    @Schema(description = "그 차수에 입금 완료가 1명이라도 있으면 true(수정·삭제 버튼 비활성)", example = "false")
    private final Boolean isLocked;
}
