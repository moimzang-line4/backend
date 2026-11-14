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
public class ScheduleListResponse {

    @Schema(description = "기간과 겹치는 일정 목록(시작 시각 오름차순)")
    private final List<ScheduleSummaryResponse> schedules;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null")
    private final String nextCursor;
}
