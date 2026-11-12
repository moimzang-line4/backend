package com.moimzang.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomCreateResponse {

    @Schema(description = "생성된 대화방 ID", example = "8")
    private final Long chatRoomId;
}
