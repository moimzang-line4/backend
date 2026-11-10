package com.moimzang.domain.meeting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingProgressResponse {

    @Schema(description = "완료된 안건 수", example = "1")
    private final Integer doneCount;

    @Schema(description = "전체 안건 수", example = "2")
    private final Integer totalCount;

    @Schema(description = "진행률(%). doneCount/totalCount를 반올림한 값", example = "50")
    private final Integer percentage;
}
