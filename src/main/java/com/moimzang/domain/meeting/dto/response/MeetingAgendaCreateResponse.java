package com.moimzang.domain.meeting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingAgendaCreateResponse {

    @Schema(description = "생성된 안건 ID", example = "3")
    private final Long agendaId;

    @Schema(description = "노출 순서", example = "2")
    private final Integer orderNo;
}
