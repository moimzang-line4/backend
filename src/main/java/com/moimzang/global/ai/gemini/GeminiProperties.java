package com.moimzang.global.ai.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

// app.ai.provider와 무관하게 항상 바인딩되므로(다른 벤더를 선택해도 이 설정은 로드됨),
// 여기서는 하드 검증하지 않는다. 실제 키 검증은 provider로 선택됐을 때만 생성되는
// GeminiClient 생성자에서 한다
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {}
