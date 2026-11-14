package com.moimzang.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleDetailResponse {

    @Schema(description = "일정 ID", example = "10")
    private final Long scheduleId;

    @Schema(description = "제목", example = "가을 정기공연")
    private final String title;

    @Schema(description = "색상", example = "RED")
    private final String color;

    @Schema(description = "일정 카테고리 ID. 없으면 null", example = "1")
    private final Long categoryId;

    @Schema(description = "일정 카테고리명. 없으면 null", example = "공연")
    private final String categoryName;

    @Schema(description = "종일 여부", example = "false")
    private final Boolean isAllDay;

    @Schema(description = "시작 일시", example = "2026-11-14T17:00:00")
    private final LocalDateTime startAt;

    @Schema(description = "종료 일시", example = "2026-11-14T21:00:00")
    private final LocalDateTime endAt;

    @Schema(description = "장소", example = "얼라이브홀")
    private final String location;

    @Schema(description = "메모", example = "리허설 15시")
    private final String memo;

    // 게터 이름이 getDDay라 그대로 두면 JSON 키가 dday로 나가서 명세의 dDay로 고정한다
    @Schema(description = "시작일까지 남은 날짜. 당일은 0, 지난 일정은 음수", example = "9")
    @Getter(onMethod_ = @JsonProperty("dDay"))
    private final Long dDay;

    @Schema(description = "운영 공간 생성 여부", example = "true")
    private final Boolean hasOperationSpace;
}
