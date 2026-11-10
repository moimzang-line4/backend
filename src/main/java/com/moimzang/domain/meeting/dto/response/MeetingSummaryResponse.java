package com.moimzang.domain.meeting.dto.response;

import com.moimzang.domain.meeting.enums.MeetingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingSummaryResponse {

    @Schema(description = "회의 ID", example = "1")
    private final Long meetingId;

    @Schema(description = "회의 제목", example = "가을 정기공연 운영진 회의")
    private final String title;

    @Schema(description = "회의 날짜", example = "2026-11-07")
    private final LocalDate meetingDate;

    @Schema(description = "회의 카테고리명", example = "운영진 회의")
    private final String categoryName;

    @Schema(description = "회의 목록 카드에 표시되는 한 줄 설명", example = "가을 공연 준비 논의")
    private final String listSummary;

    @Schema(description = "회의 상태", example = "IN_PROGRESS")
    private final MeetingStatus status;

    @Schema(description = "안건 개수", example = "3")
    private final Long agendaCount;

    @Schema(description = "참석자", example = "김가은, 홍보팀")
    private final String attendees;

    @Schema(description = "생성 일시", example = "2026-11-07T10:00:00")
    private final LocalDateTime createdAt;
}
