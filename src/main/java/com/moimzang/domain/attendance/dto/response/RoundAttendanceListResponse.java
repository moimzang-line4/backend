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
public class RoundAttendanceListResponse {

    @Schema(description = "소속 일정 제목", example = "정기 모임")
    private final String scheduleTitle;

    @Schema(description = "출석 진행 현황(필터와 무관하게 항상 전체 기준)")
    private final RoundAttendanceProgressResponse progress;

    @Schema(description = "참가자 목록")
    private final List<RoundAttendeeResponse> attendees;

    @Schema(description = "다음 페이지 cursor. 없으면 마지막 페이지", example = "MTc2MzA0MDAwMDAwMA.12")
    private final String nextCursor;
}
