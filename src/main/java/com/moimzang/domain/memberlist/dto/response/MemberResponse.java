package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 수집하지 않은 항목은 null로 내려가고 화면에서는 '—'로 보여준다
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberResponse {

    @Schema(description = "부원 ID", example = "101")
    private final Long memberId;

    @Schema(description = "이름", example = "한지우")
    private final String name;

    @Schema(description = "학과", example = "전자전기공학부")
    private final String department;

    @Schema(description = "전화번호", example = "010-6789-0123")
    private final String phone;

    @Schema(description = "학교", example = "동국대학교")
    private final String school;

    @Schema(description = "학번", example = "2026123456")
    private final String studentId;

    @Schema(description = "파트", example = "보컬")
    private final String part;

    @Schema(description = "학년", example = "1학년")
    private final String grade;
}
