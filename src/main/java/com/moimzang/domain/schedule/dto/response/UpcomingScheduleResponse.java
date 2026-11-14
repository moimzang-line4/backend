package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpcomingScheduleResponse {

    @Schema(description = "일정 ID", example = "10")
    private final Long scheduleId;

    @Schema(description = "일정 카테고리명. 없으면 null", example = "공연")
    private final String categoryName;

    @Schema(description = "시작 일시", example = "2026-11-14T17:00:00")
    private final LocalDateTime startAt;

    @Schema(description = "제목", example = "가을 정기공연")
    private final String title;

    @Schema(description = "장소", example = "얼라이브홀")
    private final String location;

    @Schema(description = "운영 메뉴 수(메모장·출석·정산·자료로 항상 4)", example = "4")
    private final Integer operationMenuCount;
}
