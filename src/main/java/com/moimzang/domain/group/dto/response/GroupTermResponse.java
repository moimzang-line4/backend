package com.moimzang.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupTermResponse {

    @Schema(description = "기수 ID", example = "1")
    private final Long termId;

    @Schema(description = "기수명", example = "26기 (2026)")
    private final String name;
}
