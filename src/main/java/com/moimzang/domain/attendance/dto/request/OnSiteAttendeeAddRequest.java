package com.moimzang.domain.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class OnSiteAttendeeAddRequest {

    @Schema(description = "이름", example = "김모임")
    @NotBlank
    @Size(max = 50)
    private String name;

    @Schema(description = "학과·소속", example = "컴퓨터공학과")
    @NotBlank
    @Size(max = 100)
    private String affiliation;

    @Schema(description = "전화번호 뒤 4자리(숫자 4자리)", example = "1234")
    @NotBlank
    @Pattern(regexp = "^\\d{4}$")
    private String phoneLast4;

    @Schema(description = "같은 이름이 있어도 다른 사람으로 새로 추가할지(기본 false)", example = "false")
    private boolean allowSameName;
}
