package com.moimzang.domain.group.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupCreateRequest {

    @Schema(description = "그룹명", example = "음샘")
    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(description = "첫 기수명(기수 구분이 없으면 연도)", example = "26기 (2026)")
    @NotBlank
    @Size(max = 100)
    private String termName;
}
