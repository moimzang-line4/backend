package com.moimzang.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReceiptRegisterRequest {

    @Schema(description = "가게명(선택)", example = "스타벅스 신촌점")
    private String storeName;

    @Schema(description = "거래일시(선택)", example = "2026-11-13T19:00:00")
    private LocalDateTime transactedAt;

    @Schema(description = "영수증 총액(양의 정수)", example = "50000")
    @NotNull
    private Integer totalAmount;

    @Schema(description = "회비 지원금(0 이상, 총액 이하)", example = "10000")
    @NotNull
    private Integer clubSubsidyAmount;

    @Schema(description = "업로드된 영수증 이미지의 스토리지 키(사진 없이 등록 시 생략)", example = "files/2026/11/ab12.jpg")
    private String imageKey;
}
