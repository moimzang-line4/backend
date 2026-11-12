package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RoundAttendeeCheckInResponse {

    @Schema(description = "참가자 ID(schedule_attendee.id)", example = "1")
    private final Long attendeeId;

    @Schema(description = "이름", example = "김모임")
    private final String name;

    @Schema(description = "출석 완료 여부", example = "true")
    private final Boolean isCheckedIn;

    @Schema(description = "출석 처리 일시(미출석이면 null)", example = "2026-11-13T19:00:00")
    private final LocalDateTime checkedInAt;

    @Schema(description = "그 차수 전체 참가자 기준 출석 진행률")
    private final RoundAttendanceProgressResponse progress;
}
