package com.moimzang.domain.meeting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingCreateResponse {

    @Schema(description = "생성된 회의 ID", example = "1")
    private final Long meetingId;

    @Schema(description = "생성된 안건 ID 목록(요청에 보낸 agendas 순서와 동일)", example = "[1, 2]")
    private final List<Long> agendaIds;
}
