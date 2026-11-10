package com.moimzang.domain.meeting.dto.response;

import com.moimzang.domain.meeting.enums.MeetingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingDetailResponse {

    @Schema(description = "회의 ID", example = "1")
    private final Long meetingId;

    @Schema(description = "회의 제목", example = "가을 정기공연 운영진 회의")
    private final String title;

    @Schema(description = "회의 날짜", example = "2026-11-07")
    private final LocalDate meetingDate;

    @Schema(description = "회의 카테고리 ID", example = "1")
    private final Long categoryId;

    @Schema(description = "참석자", example = "김가은, 홍보팀")
    private final String attendees;

    @Schema(description = "회의 상태", example = "IN_PROGRESS")
    private final MeetingStatus status;

    @Schema(description = "회의 종료 일시. 진행중이면 null", example = "null")
    private final LocalDateTime endedAt;

    @Schema(description = "안건 진행률")
    private final MeetingProgressResponse progress;

    @Schema(description = "안건 목록")
    private final List<MeetingAgendaDetailResponse> agendas;

    @Schema(description = "AI 요약 내용. 아직 요약하지 않았으면 null", example = "null")
    private final String summaryContent;

    @Schema(description = "AI 요약 생성 일시. 아직 요약하지 않았으면 null", example = "null")
    private final LocalDateTime summaryGeneratedAt;
}
