package com.moimzang.global.ai;

import java.util.List;

// 도구 호출 대화 1회의 결과. 모델은 둘 중 하나를 돌려준다 —
// 도구를 더 부르겠다(toolCalls)거나, 최종 답변 텍스트(content)다
public record AiChatResult(
        String content,
        List<AiToolCall> toolCalls
) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
