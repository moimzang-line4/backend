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
public class ChatMessageListResponse {

    @Schema(description = "메시지 목록(대화 순서대로)")
    private final List<ChatMessageResponse> messages;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null",
            example = "MjAyNi0xMS0xM1QyMTowMTowMA.201")
    private final String nextCursor;
}
