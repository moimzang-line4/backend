package com.moimzang.global.ai;

import java.util.List;

// AI 텍스트 생성 호출 전담. 벤더(Gemini/OpenAI 등)별 구현은 app.ai.provider 설정으로 갈린다.
// 실패는 AiClientException으로 통일해서 올리고, 도메인별 ErrorCode 변환은 호출하는 쪽의 책임이다
public interface AiClient {

    String generateContent(String prompt);

    // 도구 목록을 함께 넘겨 "어떤 도구를 어떤 인자로 부를지" 또는 최종 답변을 받는다.
    // 호출하는 쪽이 결과를 보고 도구를 실행한 뒤 messages에 결과를 덧붙여 다시 호출하는 식으로
    // 여러 턴을 돈다 — 그 루프 제어는 이 인터페이스가 아니라 호출하는 쪽의 책임이다
    AiChatResult chat(List<AiChatMessage> messages, List<AiTool> tools);
}
