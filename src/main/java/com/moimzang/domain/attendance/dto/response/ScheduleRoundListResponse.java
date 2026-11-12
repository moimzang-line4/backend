package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleRoundListResponse {

    @Schema(description = "차수 목록(roundNo 오름차순)")
    private final List<ScheduleRoundSummaryResponse> rounds;
}
