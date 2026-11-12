package com.moimzang.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// AI 답변 아래 '…로 이동' 버튼이 가리키는 화면.
// 경로(URL)는 내려주지 않는다 — 화면을 가진 쪽이 라우팅 규칙을 정하고, 서버는 어떤 화면의
// 어떤 대상인지만 알려준다
public record ChatNavigationResponse(
        @Schema(description = "이동할 화면", example = "SETTLEMENT",
                allowableValues = {"SETTLEMENT", "ATTENDANCE", "NOTE", "SCHEDULE", "MEETING", "FILE", "BUDGET"})
        String type,

        @Schema(description = "이동 대상 ID. SETTLEMENT·ATTENDANCE·NOTE·SCHEDULE은 일정 ID, "
                + "MEETING은 회의 ID, 기수 단위 화면인 FILE·BUDGET은 null", example = "10")
        Long targetId
) {
}
