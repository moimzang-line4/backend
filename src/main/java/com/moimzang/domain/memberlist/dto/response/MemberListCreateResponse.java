package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListCreateResponse {

    @Schema(description = "명단방 ID", example = "1")
    private final Long memberListId;

    @Schema(description = "등록 링크 토큰", example = "Qm9hcmQtOGY0Yw")
    private final String joinToken;

    @Schema(description = "등록 링크 경로. 프론트 도메인을 앞에 붙여 쓴다", example = "/join/Qm9hcmQtOGY0Yw")
    private final String joinUrl;
}
