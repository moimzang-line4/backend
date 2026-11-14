package com.moimzang.domain.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OperationSpaceListResponse {

    @Schema(description = "진행 중/지난 기록·유형·검색어 조건에 맞는 전체 운영 기록 수", example = "3")
    private final Long totalCount;

    @Schema(description = "운영 기록 목록(일정·회의록 통합)")
    private final List<OperationSpaceItemResponse> items;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null")
    private final String nextCursor;
}
