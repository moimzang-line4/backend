package com.moimzang.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomListResponse {

    @Schema(description = "대화방 목록(최근 활동 순)")
    private final List<ChatRoomSummaryResponse> chatRooms;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null",
            example = "MjAyNi0xMS0xM1QyMTowMTowMA.8")
    private final String nextCursor;

    @Schema(description = "전체 대화방 수. 첫 페이지에서만 값이 있고 이후 페이지는 null", example = "3")
    private final Long totalCount;
}
