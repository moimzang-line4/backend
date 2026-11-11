package com.moimzang.global.ai;

// AI 텍스트 생성 호출 전담. 벤더(Gemini/OpenAI 등)별 구현은 app.ai.provider 설정으로 갈린다.
// 실패는 AiClientException으로 통일해서 올리고, 도메인별 ErrorCode 변환은 호출하는 쪽의 책임이다
public interface AiClient {

    String generateContent(String prompt);
}
