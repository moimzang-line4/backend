package com.moimzang.domain.meeting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingSummaryGenerateResponse {

    @Schema(description = "요약에 반영된 안건 수", example = "3")
    private final Integer summarizedAgendaCount;

    @Schema(description = "회의 전체 요약", example = "가을 정기공연 준비를 위해 관객 참여 이벤트, 공연 홍보 일정, 장비 점검 계획을 논의했어요.")
    private final String summaryContent;

    @Schema(description = "요약 생성 일시", example = "2026-11-11T15:30:00")
    private final LocalDateTime summaryGeneratedAt;
}
