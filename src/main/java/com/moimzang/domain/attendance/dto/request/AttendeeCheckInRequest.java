package com.moimzang.domain.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttendeeCheckInRequest {

    @Schema(description = "출석 완료 여부(true면 출석 처리, false면 되돌리기)", example = "true")
    @NotNull
    private Boolean isCheckedIn;
}
