package com.moimzang.global.ai;

// 모델이 "이 도구를 이 인자로 불러달라"고 돌려준 요청.
// argumentsJson은 아직 파싱하지 않은 원본 문자열이다 — 모델이 스키마를 어긴 JSON을 줄 수도 있어서,
// 파싱 실패를 도구 실행 단계에서 잡아 모델에게 되돌려주기 위함이다
public record AiToolCall(
        String id,
        String name,
        String argumentsJson
) {}
