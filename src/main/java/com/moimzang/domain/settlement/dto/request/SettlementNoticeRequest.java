package com.moimzang.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementNoticeRequest {

    @Schema(description = "공지에 포함할 차수 ID(schedule_round.id) 목록. 1개 이상", example = "[1, 2]")
    @NotNull
    private List<Long> roundIds;
}
