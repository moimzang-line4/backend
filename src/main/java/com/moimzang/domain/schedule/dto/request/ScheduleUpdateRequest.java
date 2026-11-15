package com.moimzang.domain.schedule.dto.request;

import com.moimzang.domain.schedule.enums.ScheduleColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 수정하는 부분 수정 요청이라 전부 선택값이다 — null이면 그 필드는 바뀌지 않는다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleUpdateRequest {

    @Schema(description = "제목(선택)", example = "가을 정기공연")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String title;

    @Schema(description = "색상(선택)", example = "BLUE")
    private ScheduleColor color;

    @Schema(description = "일정 카테고리 ID(선택)", example = "1")
    private Long categoryId;

    @Schema(description = "종일 여부(선택)", example = "false")
    private Boolean isAllDay;

    @Schema(description = "시작 일시(선택)", example = "2026-11-14T17:00:00")
    private LocalDateTime startAt;

    @Schema(description = "종료 일시(선택)", example = "2026-11-14T21:00:00")
    private LocalDateTime endAt;

    @Schema(description = "장소(선택). 빈 문자열을 보내면 지운다", example = "얼라이브홀")
    @Size(max = 255)
    private String location;

    @Schema(description = "메모(선택). 빈 문자열을 보내면 지운다", example = "리허설 15시")
    private String memo;

    @Schema(description = "운영 공간 켜기(선택). true면 꺼진 운영 공간을 켜고, 켜진 운영 공간을 false로 끌 수는 없다",
            example = "true")
    private Boolean createOperationSpace;
}
