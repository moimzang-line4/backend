package com.moimzang.domain.schedule.dto.request;

import com.moimzang.domain.schedule.enums.ScheduleColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleCreateRequest {

    @Schema(description = "제목", example = "가을 정기공연")
    @NotBlank
    @Size(max = 100)
    private String title;

    @Schema(description = "색상", example = "RED")
    @NotNull
    private ScheduleColor color;

    @Schema(description = "일정 카테고리 ID(선택)", example = "1")
    private Long categoryId;

    @Schema(description = "종일 여부", example = "false")
    @NotNull
    private Boolean isAllDay;

    @Schema(description = "시작 일시", example = "2026-11-14T17:00:00")
    @NotNull
    private LocalDateTime startAt;

    @Schema(description = "종료 일시(시작보다 빠를 수 없음)", example = "2026-11-14T21:00:00")
    @NotNull
    private LocalDateTime endAt;

    @Schema(description = "장소(선택)", example = "얼라이브홀")
    @Size(max = 255)
    private String location;

    @Schema(description = "메모(선택)", example = "리허설 15시")
    private String memo;

    @Schema(description = "운영 공간 함께 만들기", example = "true")
    @NotNull
    private Boolean createOperationSpace;
}
