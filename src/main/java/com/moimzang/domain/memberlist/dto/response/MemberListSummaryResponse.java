package com.moimzang.domain.memberlist.dto.response;

import com.moimzang.domain.memberlist.enums.MemberListStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListSummaryResponse {

    @Schema(description = "명단방 ID", example = "1")
    private final Long memberListId;

    @Schema(description = "명단방 이름", example = "2026년 2학기 음샘")
    private final String name;

    @Schema(description = "등록 인원", example = "6")
    private final Long memberCount;

    @Schema(description = "등록중(RECRUITING)/등록완료(COMPLETED). 마감일이 지나면 등록완료", example = "RECRUITING")
    private final MemberListStatus status;

    @Schema(description = "등록 마감일", example = "2026-11-30")
    private final LocalDate registerDeadline;
}
