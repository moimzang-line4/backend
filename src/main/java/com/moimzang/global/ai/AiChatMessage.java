package com.moimzang.global.ai;

import java.util.List;

// 도구 호출 대화 한 턴. 벤더 요청의 messages 배열 한 칸에 대응한다.
// role이 TOOL이면 toolCallId로 어떤 호출의 결과인지 가리키고, ASSISTANT가 도구를 호출한 턴이면
// toolCalls가 채워진다
public record AiChatMessage(
        Role role,
        String content,
        String toolCallId,
        List<AiToolCall> toolCalls
) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage(Role.SYSTEM, content, null, null);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(Role.USER, content, null, null);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(Role.ASSISTANT, content, null, null);
    }

    // 모델이 도구를 호출한 턴 — 다음 요청에 그대로 되돌려줘야 벤더가 대화를 이어붙인다
    public static AiChatMessage assistantToolCalls(List<AiToolCall> toolCalls) {
        return new AiChatMessage(Role.ASSISTANT, null, null, toolCalls);
    }

    // 도구 실행 결과 — 어떤 호출에 대한 응답인지 toolCallId로 짝지어야 한다
    public static AiChatMessage toolResult(String toolCallId, String content) {
        return new AiChatMessage(Role.TOOL, content, toolCallId, null);
    }
}
