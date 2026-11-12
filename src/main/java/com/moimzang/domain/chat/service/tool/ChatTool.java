package com.moimzang.domain.chat.service.tool;

import tools.jackson.databind.JsonNode;
import com.moimzang.global.ai.AiTool;

// AI 비서가 호출할 수 있는 조회 도구 하나.
// 도구는 질문 종류가 아니라 도메인 데이터 범위 단위로 만든다 — 그래야 미리 예측하지 못한 질문도
// "넓게 가져온 결과 안에서 AI가 직접 추려내는" 식으로 답할 수 있다.
// 구현체는 전부 읽기 전용이며 termId 밖의 데이터를 조회하지 않는다
public interface ChatTool {

    // AI에게 보낼 선언. 모델은 description만 보고 도구를 고르므로 반환 내용과 선행 호출 순서까지 적는다
    AiTool definition();

    // 조회를 실행한다. 반환값은 그대로 JSON으로 직렬화돼 AI에게 전달된다.
    // args는 모델이 만든 것이라 스키마를 어길 수 있다 — 잘못된 인자는 ChatToolArgumentException으로
    // 올려서 AI가 고쳐 부를 수 있게 한다
    Object execute(Long termId, JsonNode args);
}
