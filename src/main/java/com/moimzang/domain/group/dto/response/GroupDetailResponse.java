package com.moimzang.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupDetailResponse {

    @Schema(description = "그룹 ID", example = "1")
    private final Long groupId;

    @Schema(description = "그룹명", example = "음샘")
    private final String name;

    @Schema(description = "기수 목록(만든 순서)")
    private final List<GroupTermResponse> terms;
}
