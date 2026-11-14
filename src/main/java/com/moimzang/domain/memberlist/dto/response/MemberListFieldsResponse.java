package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 이름·학과는 항상 받는 항목이라 여기에는 토글로 켜고 끄는 항목만 담는다
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListFieldsResponse {

    @Schema(description = "학교 수집", example = "true")
    private final Boolean school;

    @Schema(description = "학번 수집", example = "true")
    private final Boolean studentId;

    @Schema(description = "파트 수집", example = "false")
    private final Boolean part;

    @Schema(description = "학년 수집", example = "false")
    private final Boolean grade;

    @Schema(description = "전화번호 수집", example = "true")
    private final Boolean phone;
}
