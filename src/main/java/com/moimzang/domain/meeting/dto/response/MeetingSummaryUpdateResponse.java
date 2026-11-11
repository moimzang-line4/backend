package com.moimzang.domain.meeting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingSummaryUpdateResponse {

    @Schema(description = "회의 ID", example = "1")
    private final Long meetingId;
}
