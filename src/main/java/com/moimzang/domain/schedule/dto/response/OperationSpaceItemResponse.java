package com.moimzang.domain.schedule.dto.response;

import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OperationSpaceItemResponse {

    @Schema(description = "항목 유형(SCHEDULE 또는 MEETING)", example = "SCHEDULE")
    private final OperationSpaceType type;

    @Schema(description = "일정 ID 또는 회의 ID", example = "10")
    private final Long id;

    @Schema(description = "제목", example = "가을 정기공연")
    private final String title;

    @Schema(description = "날짜(일정은 시작일, 회의는 회의 날짜)", example = "2026-11-14")
    private final LocalDate date;

    @Schema(description = "진행 중/지난 기록", example = "ONGOING")
    private final OperationSpaceStatus status;
}
