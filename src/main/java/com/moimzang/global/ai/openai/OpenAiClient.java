package com.moimzang.global.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moimzang.global.ai.AiChatMessage;
import com.moimzang.global.ai.AiChatResult;
import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.global.ai.AiTool;
import com.moimzang.global.ai.AiToolCall;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
                properties.model(), List.of(Message.user(prompt)), null, null);

        return extractText(call(request));
    }

    @Override
    public AiChatResult chat(List<AiChatMessage> messages, List<AiTool> tools) {
        List<Message> body = messages.stream().map(OpenAiClient::toMessage).toList();
        List<Tool> requestTools = (tools == null || tools.isEmpty())
                ? null
                : tools.stream().map(OpenAiClient::toTool).toList();
        // 도구를 넘길 때만 tool_choice를 실어야 한다 — 도구 없이 "auto"를 보내면 OpenAI가 400을 낸다
        String toolChoice = requestTools == null ? null : "auto";

        ChatCompletionResponse response = call(
                new ChatCompletionRequest(properties.model(), body, requestTools, toolChoice));

        Message message = firstMessage(response);
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<AiToolCall> toolCalls = message.toolCalls().stream()
                    .map(c -> new AiToolCall(c.id(), c.function().name(), c.function().arguments()))
                    .toList();
            return new AiChatResult(message.content(), toolCalls);
        }

        if (message.content() == null || message.content().isBlank()) {
            // 안전 필터 등으로 도구 호출도 본문도 없는 choice가 올 수 있다 — 빈 답변을 성공으로 다루지 않는다
            throw new AiClientException("OpenAI 응답에 content와 tool_calls가 모두 없습니다.");
        }
        return new AiChatResult(message.content(), null);
    }

    private ChatCompletionResponse call(ChatCompletionRequest request) {
        URI uri = URI.create(properties.baseUrl() + "/v1/chat/completions");
        try {
            return restClient.post()
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
    }

    private String extractText(ChatCompletionResponse response) {
        Message message = firstMessage(response);
        if (message.content() == null || message.content().isBlank()) {
            // 안전 필터 등으로 content 없는 choice가 올 수 있다 — null/빈 요약을 성공으로 저장하지 않는다
            throw new AiClientException("OpenAI 응답에 content가 없습니다.");
        }
        return message.content();
    }

    private Message firstMessage(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiClientException("OpenAI 응답에 choices가 없습니다.");
        }
        Message message = response.choices().get(0).message();
        if (message == null) {
            throw new AiClientException("OpenAI 응답에 message가 없습니다.");
        }
        return message;
    }

    private static Message toMessage(AiChatMessage message) {
        List<ToolCall> toolCalls = message.toolCalls() == null ? null : message.toolCalls().stream()
                .map(c -> new ToolCall(c.id(), "function", new FunctionCall(c.name(), c.argumentsJson())))
                .toList();
        return new Message(message.role().name().toLowerCase(), message.content(),
                message.toolCallId(), toolCalls);
    }

    private static Tool toTool(AiTool tool) {
        return new Tool("function", new Function(tool.name(), tool.description(), tool.parametersSchema()));
    }

    // OpenAI는 snake_case를 쓰는데 이 프로젝트엔 Jackson 네이밍 전략 설정이 없어서(기본 camelCase)
    // 어긋나는 필드마다 @JsonProperty로 직접 맞춘다.
    // NON_NULL이 없으면 도구를 안 쓰는 호출에서 tools: null이 그대로 실려 OpenAI가 400을 낸다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(String model, List<Message> messages, List<Tool> tools,
                                          @JsonProperty("tool_choice") String toolChoice) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Message(String role, String content,
                           @JsonProperty("tool_call_id") String toolCallId,
                           @JsonProperty("tool_calls") List<ToolCall> toolCalls) {

        static Message user(String content) {
            return new Message("user", content, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ToolCall(String id, String type, FunctionCall function) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record FunctionCall(String name, String arguments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Tool(String type, Function function) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Function(String name, String description, Map<String, Object> parameters) {}

    private record ChatCompletionResponse(List<Choice> choices) {}

    private record Choice(Message message) {}
}
