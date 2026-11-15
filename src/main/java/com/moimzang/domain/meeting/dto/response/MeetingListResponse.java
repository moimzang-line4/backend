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
public class MeetingListResponse {

    @Schema(description = "회의 목록")
    private final List<MeetingSummaryResponse> meetings;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null", example = "MjAyNi0xMS0wNw.5")
    private final String nextCursor;

    @Schema(description = "전체 회의 수. 첫 페이지에서만 값이 있고 이후 페이지는 null", example = "4")
    private final Long totalCount;
}
