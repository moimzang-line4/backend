package com.moimzang.domain.chat.dto.response;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageResponse {

    @Schema(description = "메시지 ID", example = "201")
    private final Long messageId;

    @Schema(description = "발신자", example = "ASSISTANT", allowableValues = {"USER", "ASSISTANT"})
    private final String role;

    @Schema(description = "응답 텍스트", example = "가을공연 미입금자를 확인했어요.")
    private final String content;

    @Schema(description = "답변 아래 '…로 이동' 버튼이 가리킬 화면. 이동할 곳이 없으면 null")
    private final ChatNavigationResponse navigation;

    @Schema(description = "결과 카드 유형. 카드가 없으면 null", example = "미입금자 조회")
    private final String cardType;

    @Schema(description = "결과 카드 상세. 전송 시점 스냅샷이라 이후 입금 상태가 바뀌어도 갱신되지 않는다")
    private final JsonNode card;

    @Schema(description = "생성 시각", example = "2026-11-13T21:01:00")
    private final LocalDateTime createdAt;
}
