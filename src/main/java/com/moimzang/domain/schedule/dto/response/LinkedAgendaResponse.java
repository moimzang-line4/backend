package com.moimzang.domain.schedule.dto.response;

import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LinkedAgendaResponse {

    @Schema(description = "연결 ID", example = "7")
    private final Long linkId;

    @Schema(description = "회의 ID", example = "3")
    private final Long meetingId;

    @Schema(description = "회의 제목", example = "가을 정기공연 운영진 회의")
    private final String meetingTitle;

    @Schema(description = "회의 날짜", example = "2026-11-07")
    private final LocalDate meetingDate;

    @Schema(description = "회의 상태. COMPLETED면 안건 논의 내용·결정 사항을 읽기 전용으로 보여준다", example = "IN_PROGRESS")
    private final MeetingStatus meetingStatus;

    @Schema(description = "안건 ID", example = "12")
    private final Long agendaId;

    @Schema(description = "안건 순서(회의 안에서의 원래 순서)", example = "1")
    private final Integer agendaOrderNo;

    @Schema(description = "안건 내용", example = "공연 홍보 계획")
    private final String agendaContent;

    @Schema(description = "안건 상태", example = "COMPLETED")
    private final AgendaStatus agendaStatus;

    @Schema(description = "논의 내용", example = "인스타 릴스 3개 업로드")
    private final String discussionNote;

    @Schema(description = "결정 사항", example = "홍보팀이 다음 주까지 제작")
    private final String decision;

    @Schema(description = "결정 사항 입력 여부(공백만 있으면 미입력)", example = "true")
    private final Boolean hasDecision;
}
