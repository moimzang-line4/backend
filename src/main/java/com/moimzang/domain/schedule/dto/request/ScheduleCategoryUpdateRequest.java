package com.moimzang.domain.schedule.dto.request;

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
// 카테고리는 이름만 바꿀 수 있다
public class ScheduleCategoryUpdateRequest {

    @Schema(description = "바꿀 카테고리명", example = "공연")
    @NotBlank
    @Size(max = 50)
    private String name;
}
