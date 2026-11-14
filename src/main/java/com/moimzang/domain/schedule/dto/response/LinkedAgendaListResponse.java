package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LinkedAgendaListResponse {

    @Schema(description = "연결된 안건 카드 목록(최근 회의 먼저, 회의 안에서는 안건 순서)")
    private final List<LinkedAgendaResponse> links;
}
