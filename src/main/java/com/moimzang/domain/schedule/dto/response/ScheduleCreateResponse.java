package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleCreateResponse {

    @Schema(description = "일정 ID", example = "10")
    private final Long scheduleId;

    @Schema(description = "운영 공간 생성 여부", example = "true")
    private final Boolean hasOperationSpace;
}
