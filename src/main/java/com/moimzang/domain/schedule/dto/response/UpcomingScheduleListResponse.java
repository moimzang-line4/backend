package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpcomingScheduleListResponse {

    @Schema(description = "다가오는 운영 일정(시작 시각 오름차순)")
    private final List<UpcomingScheduleResponse> schedules;
}
