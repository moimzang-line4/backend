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
public class ReceiptOcrResponse {

    @Schema(description = "인식된 가게명(인식 실패 시 null)", example = "스타벅스 신촌점")
    private final String storeName;

    @Schema(description = "인식된 거래일시(인식 실패 시 null)")
    private final LocalDateTime transactedAt;

    @Schema(description = "인식된 영수증 총액(인식 실패 시 null)", example = "50000")
    private final Integer totalAmount;

    @Schema(description = "저장된 영수증 이미지의 스토리지 키. API-034 등록 시 imageKey로 그대로 전달한다",
            example = "receipts/2026/11/ab12.jpg")
    private final String imageKey;
}
