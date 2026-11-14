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
public class MemberPageResponse {

    @Schema(description = "검색 조건에 맞는 전체 인원", example = "8")
    private final Long totalCount;

    @Schema(description = "부원 목록(등록한 순서)")
    private final List<MemberResponse> members;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null", example = "MjAyNi0xMS0wN1QxMDozMA.12")
    private final String nextCursor;
}
