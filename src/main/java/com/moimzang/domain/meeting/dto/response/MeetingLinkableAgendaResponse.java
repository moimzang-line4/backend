package com.moimzang.domain.meeting.dto.response;

import com.moimzang.domain.meeting.enums.AgendaStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingLinkableAgendaResponse {

    @Schema(description = "안건 ID", example = "1")
    private final Long agendaId;

    @Schema(description = "안건이 속한 회의 ID", example = "1")
    private final Long meetingId;

    @Schema(description = "안건이 속한 회의 제목", example = "가을 정기공연 운영진 회의")
    private final String meetingTitle;

    @Schema(description = "안건이 속한 회의 날짜", example = "2026-11-07")
    private final LocalDate meetingDate;

    @Schema(description = "회의 안에서의 노출 순서", example = "0")
    private final Integer orderNo;

    @Schema(description = "안건 내용", example = "관객 참여 이벤트")
    private final String content;

    @Schema(description = "안건 상태", example = "COMPLETED")
    private final AgendaStatus status;

    @Schema(description = "논의 내용", example = "참여 이벤트 2종 진행하기로 함")
    private final String discussionNote;

    @Schema(description = "결정 사항", example = "경품은 홍보팀이 준비")
    private final String decision;

    @Schema(description = "결정 사항 입력 여부. 모달 카드의 '결정 사항 입력됨/미입력' 표시에 쓴다", example = "true")
    private final boolean hasDecision;
}
