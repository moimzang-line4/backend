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
public class MeetingAgendaUpdateResponse {

    @Schema(description = "안건 ID", example = "3")
    private final Long agendaId;

    @Schema(description = "안건 상태", example = "COMPLETED")
    private final AgendaStatus status;

    @Schema(description = "회의 전체 진행률")
    private final MeetingProgressResponse progress;
}
