package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RoundAttendanceProgressResponse {

    @Schema(description = "출석 완료 인원 수", example = "3")
    private final Long checkedCount;

    @Schema(description = "그 차수 전체 참가자 수(필터와 무관)", example = "5")
    private final Long totalCount;

    @Schema(description = "출석률(%). checkedCount/totalCount를 반올림한 정수", example = "60")
    private final Integer rate;
}
