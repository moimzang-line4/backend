package com.moimzang.global.ai.openai;

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

// OpenAI Chat Completions 호출 전담
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiClient implements AiClient {

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiClient(OpenAiProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("app.ai.provider=openai인데 OPENAI_API_KEY가 설정되지 않았습니다.");
        }
        this.properties = properties;

        // 타임아웃 없이 두면 OpenAI가 멈췄을 때 호출 스레드가 무한정 블록된다
        // (generateSummary가 @Transactional이라 DB 커넥션까지 같이 물려있게 됨)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String generateContent(String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(), List.of(new Message("user", prompt)));

        URI uri = URI.create(properties.baseUrl() + "/v1/chat/completions");

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패·4xx/5xx 응답 등 RestClient가 던지는 건 전부 여기로 모은다
            throw new AiClientException("OpenAI 호출에 실패했습니다.", e);
        }

        return extractText(response);
    }

    private String extractText(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiClientException("OpenAI 응답에 choices가 없습니다.");
        }
        Message message = response.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            // 안전 필터 등으로 content 없는 choice가 올 수 있다 — null/빈 요약을 성공으로 저장하지 않는다
            throw new AiClientException("OpenAI 응답에 content가 없습니다.");
        }
        return message.content();
    }

    private record ChatCompletionRequest(String model, List<Message> messages) {}

    private record ChatCompletionResponse(List<Choice> choices) {}

    private record Choice(Message message) {}

    private record Message(String role, String content) {}
}
