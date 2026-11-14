package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberJoinFormResponse {

    @Schema(description = "그룹 이름", example = "음샘")
    private final String groupName;

    @Schema(description = "명단방 이름", example = "2026년 2학기 음샘")
    private final String memberListName;

    @Schema(description = "등록 마감일", example = "2026-11-30")
    private final LocalDate registerDeadline;

    @Schema(description = "폼에 보여줄 항목. true인 항목은 필수 입력, false인 항목은 폼에서 숨긴다")
    private final MemberListFieldsResponse fields;
}
