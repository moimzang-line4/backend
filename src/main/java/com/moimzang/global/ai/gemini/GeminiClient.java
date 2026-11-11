package com.moimzang.global.ai.gemini;

import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Gemini generateContent 호출 전담
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiClient implements AiClient {

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("app.ai.provider=gemini인데 GEMINI_API_KEY가 설정되지 않았습니다.");
        }
        this.properties = properties;

        // 타임아웃 없이 두면 Gemini가 멈췄을 때 호출 스레드가 무한정 블록된다
        // (generateSummary가 @Transactional이라 DB 커넥션까지 같이 물려있게 됨)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String generateContent(String prompt) {
        GenerateContentRequest request = new GenerateContentRequest(
                List.of(new Content(List.of(new Part(prompt)))));

        // 모델명에 URI 템플릿을 쓰면 "://"가 퍼센트 인코딩돼 호스트가 사라진다.
        // 완성된 URI를 직접 만들어 그대로 넘긴다. 키는 쿼리파라미터가 아니라 헤더로 보낸다 —
        // URL에 실으면 타임아웃 등 예외 메시지에 요청 URI가 그대로 찍혀 로그에 키가 노출된다
        URI uri = URI.create(properties.baseUrl() + "/v1beta/models/" + properties.model() + ":generateContent");

        GenerateContentResponse response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateContentResponse.class);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패·4xx/5xx 응답 등 RestClient가 던지는 건 전부 여기로 모은다
            throw new AiClientException("Gemini 호출에 실패했습니다.", e);
        }

        return extractText(response);
    }

    private String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AiClientException("Gemini 응답에 candidates가 없습니다.");
        }
        Content content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new AiClientException("Gemini 응답에 parts가 없습니다.");
        }
        String text = content.parts().get(0).text();
        if (text == null || text.isBlank()) {
            // 안전 필터 등으로 text 없는 part가 올 수 있다 — null/빈 요약을 성공으로 저장하지 않는다
            throw new AiClientException("Gemini 응답에 text가 없습니다.");
        }
        return text;
    }

    private record GenerateContentRequest(List<Content> contents) {}

    private record GenerateContentResponse(List<Candidate> candidates) {}

    private record Candidate(Content content) {}

    private record Content(List<Part> parts) {}

    private record Part(String text) {}
}
