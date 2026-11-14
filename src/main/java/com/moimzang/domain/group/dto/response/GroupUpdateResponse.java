package com.moimzang.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupUpdateResponse {

    @Schema(description = "그룹 ID", example = "1")
    private final Long groupId;

    @Schema(description = "그룹명", example = "음샘")
    private final String name;

    @Schema(description = "현재 기수 ID", example = "2")
    private final Long termId;

    @Schema(description = "현재 기수명", example = "27기 (2027)")
    private final String termName;
}
