package com.moimzang.domain.meeting.dto.response;

import com.moimzang.domain.meeting.enums.MeetingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingEndResponse {

    @Schema(description = "회의 ID", example = "1")
    private final Long meetingId;

    @Schema(description = "회의 상태", example = "COMPLETED")
    private final MeetingStatus status;

    @Schema(description = "회의 종료 일시", example = "2026-11-07T15:30:00")
    private final LocalDateTime endedAt;

    @Schema(description = "완료된 안건 수", example = "2")
    private final Long doneAgendaCount;

    @Schema(description = "전체 안건 수", example = "3")
    private final Long totalAgendaCount;
}
