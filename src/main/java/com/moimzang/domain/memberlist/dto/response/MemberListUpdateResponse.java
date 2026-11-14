package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListUpdateResponse {

    @Schema(description = "명단방 ID", example = "1")
    private final Long memberListId;
}
