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
public class RoundAttendeeResponse {

    @Schema(description = "참가자 ID(schedule_attendee.id)", example = "1")
    private final Long attendeeId;

    @Schema(description = "이름", example = "김모임")
    private final String name;

    @Schema(description = "학과·소속", example = "컴퓨터공학과")
    private final String affiliation;

    @Schema(description = "전화번호 뒤 4자리", example = "1234")
    private final String phoneLast4;

    @Schema(description = "출석 완료 여부", example = "false")
    private final Boolean isCheckedIn;

    @Schema(description = "출석 처리 일시(미출석이면 null)", example = "2026-11-13T19:00:00")
    private final LocalDateTime checkedInAt;
}
