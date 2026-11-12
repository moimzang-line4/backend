package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleRoundCreateResponse {

    @Schema(description = "생성된 차수 ID", example = "1")
    private final Long roundId;

    @Schema(description = "차수 번호(마지막 차수+1, 없으면 1)", example = "1")
    private final Integer roundNo;

    @Schema(description = "차수 표시명(\"{roundNo}차\"로 자동 생성)", example = "1차")
    private final String label;
}
