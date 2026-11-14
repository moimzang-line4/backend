package com.moimzang.domain.memberlist.dto.response;

import com.moimzang.domain.memberlist.enums.MemberListStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListDetailResponse {

    @Schema(description = "명단방 ID", example = "1")
    private final Long memberListId;

    @Schema(description = "명단방 이름", example = "2026년 2학기 음샘")
    private final String name;

    @Schema(description = "등록 마감 일시", example = "2026-11-30T23:59:00")
    private final LocalDateTime registerDeadline;

    @Schema(description = "등록중/등록완료", example = "RECRUITING")
    private final MemberListStatus status;

    @Schema(description = "등록 인원", example = "6")
    private final Long memberCount;

    @Schema(description = "등록 링크", example = "moimjang.app/join/Qm9hcmQtOGY0Yw")
    private final String joinUrl;

    @Schema(description = "링크 닫힘 여부", example = "false")
    private final Boolean isLinkClosed;

    @Schema(description = "수집 항목(이름·학과는 항상 수집)")
    private final MemberListFieldsResponse fields;
}
