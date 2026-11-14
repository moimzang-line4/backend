package com.moimzang.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupCreateResponse {

    @Schema(description = "그룹 ID", example = "1")
    private final Long groupId;

    @Schema(description = "생성된 기수 ID", example = "1")
    private final Long termId;
}
