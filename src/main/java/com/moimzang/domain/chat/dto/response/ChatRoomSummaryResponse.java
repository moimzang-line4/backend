package com.moimzang.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomSummaryResponse {

    @Schema(description = "대화방 ID", example = "8")
    private final Long chatRoomId;

    @Schema(description = "대화방 제목. 아직 질문을 보내지 않아 비어 있으면 null",
            example = "가을공연 미입금자 알려줘")
    private final String title;

    @Schema(description = "마지막 활동 일시(정렬 기준)", example = "2026-11-13T21:01:00")
    private final LocalDateTime updatedAt;
}
