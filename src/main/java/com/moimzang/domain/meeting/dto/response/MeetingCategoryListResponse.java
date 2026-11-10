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
public class MeetingCategoryListResponse {

    @Schema(description = "회의 카테고리 목록")
    private final List<MeetingCategoryResponse> categories;
}
