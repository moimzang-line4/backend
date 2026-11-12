package com.moimzang.global.ai;

import java.util.Map;

// AI에게 "호출할 수 있는 도구"로 선언하는 정의.
// 모델은 description만 보고 도구를 고르므로, 설명에 무엇을 반환하는지와 선행 호출 순서까지 담는다.
// parametersSchema는 JSON Schema 형태의 맵이며 벤더 요청 바디에 그대로 직렬화된다
public record AiTool(
        String name,
        String description,
        Map<String, Object> parametersSchema
) {}
