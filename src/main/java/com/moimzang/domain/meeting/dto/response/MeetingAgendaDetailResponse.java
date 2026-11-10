package com.moimzang.domain.meeting.dto.response;

import com.moimzang.domain.meeting.enums.AgendaStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingAgendaDetailResponse {

    @Schema(description = "안건 ID", example = "1")
    private final Long agendaId;

    @Schema(description = "노출 순서", example = "0")
    private final Integer orderNo;

    @Schema(description = "안건 내용", example = "관객 참여 이벤트")
    private final String content;

    @Schema(description = "논의 내용", example = "참여 이벤트 2종 진행하기로 함")
    private final String discussionNote;

    @Schema(description = "결정 사항", example = "경품은 홍보팀이 준비")
    private final String decision;

    @Schema(description = "안건 상태", example = "COMPLETED")
    private final AgendaStatus status;
}
