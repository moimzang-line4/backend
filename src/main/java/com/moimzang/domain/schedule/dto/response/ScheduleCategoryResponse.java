package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleCategoryResponse {

    @Schema(description = "카테고리 ID", example = "1")
    private final Long categoryId;

    @Schema(description = "카테고리명", example = "공연")
    private final String name;
}
