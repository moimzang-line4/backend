package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberUpdateResponse {

    @Schema(description = "부원 ID", example = "101")
    private final Long memberId;
}
