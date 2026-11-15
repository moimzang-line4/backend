package com.moimzang.domain.settlement.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 영수증 인식(API-036) 전용 OpenAI 호출 설정. api-key는 global/ai/openai의 OpenAiProperties가
// 이미 OPENAI_API_KEY를 들고 있어 그대로 재사용하고, 여기서는 model/base-url/timeout만 둔다 —
// meeting이 쓰는 모델(app.ai.openai.model)과 영수증 인식 모델을 독립적으로 바꿀 수 있어야 해서다
@ConfigurationProperties(prefix = "app.ocr.openai")
public record ReceiptOcrOpenAiProperties(
        String model,
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {}
