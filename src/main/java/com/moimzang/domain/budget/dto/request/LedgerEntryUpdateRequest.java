package com.moimzang.domain.budget.dto.request;

import com.moimzang.domain.budget.enums.LedgerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 반영하는 부분 수정 요청이다 — null은 기존 값을 유지한다는 뜻이라 전부 선택 항목이고,
// 보낸 경우에만 유효성을 검사한다(Bean Validation 표준 동작: 대부분의 제약은 null을 통과시킨다)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntryUpdateRequest {

    @Schema(description = "항목 유형(생략하면 유지)", example = "EXPENSE")
    private LedgerType type;

    @Schema(description = "항목명(생략하면 유지, 보냈다면 공백 불가)", example = "간식비")
    @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다.")
    @Size(max = 100)
    private String itemName;

    @Schema(description = "금액(생략하면 유지, 보냈다면 양의 정수)", example = "15000")
    @Positive
    private Integer amount;

    @Schema(description = "거래 일자(생략하면 유지)", example = "2026-11-15")
    private LocalDate entryDate;
}
