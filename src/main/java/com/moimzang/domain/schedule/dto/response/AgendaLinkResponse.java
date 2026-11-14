package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgendaLinkResponse {

    @Schema(description = "연결 ID", example = "7")
    private final Long linkId;

    @Schema(description = "안건 ID", example = "12")
    private final Long agendaId;
}
