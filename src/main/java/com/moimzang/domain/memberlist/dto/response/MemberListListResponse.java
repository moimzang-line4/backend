package com.moimzang.domain.memberlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListListResponse {

    @Schema(description = "명단방 목록(만든 순서)")
    private final List<MemberListSummaryResponse> memberLists;
}
