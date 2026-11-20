package com.moimzang.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupSummaryListResponse {

    @Schema(description = "그룹 목록(요청한 ids 순서, 존재하지 않는 id는 제외)")
    private final List<GroupSummaryResponse> groups;
}
