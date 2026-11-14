package com.moimzang.global.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moimzang.global.ai.ReceiptOcrClient;
import com.moimzang.global.ai.ReceiptOcrException;
import com.moimzang.global.ai.ReceiptOcrResult;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Gemini generateContent에 이미지를 멀티모달로 보내 영수증을 인식한다. app.ai.provider와
// 무관하게 항상 등록한다 — 영수증 인식은 벤더 전환 대상이 아니고(결정사항), 키가 없어도
// 앱 기동이 막히면 안 되므로(다른 팀원 로컬 실행·CI) 생성자가 아니라 호출 시점에 키를 검사한다
@Component
public class GeminiReceiptOcrClient implements ReceiptOcrClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROMPT = """
            한국 영수증 이미지를 분석해 다음 세 항목을 JSON으로 추출해라.
            - storeName: 상호(가게 이름)
            - transactedAt: 거래일시. ISO-8601(yyyy-MM-ddTHH:mm:ss) 형식의 문자열로 반환해라.
            - totalAmount: 최종 결제 총액. 원 단위의 정수로 반환해라(할인·부가세가 반영된 최종
              결제 금액이어야 한다).
            영수증이 아니거나 해당 항목을 이미지에서 찾을 수 없으면 그 항목은 null로 반환해라.
            추측해서 지어내지 마라.
            """;

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiReceiptOcrClient(GeminiProperties properties) {
        this.properties = properties;

        // 타임아웃 없이 두면 Gemini가 멈췄을 때 호출 스레드가 무한정 블록된다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ReceiptOcrResult recognize(byte[] imageBytes, String contentType) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ReceiptOcrException("app.ai.gemini.api-key가 설정되지 않았습니다.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        GenerateContentRequest request = new GenerateContentRequest(
                List.of(new RequestContent(List.of(
                        new Part(PROMPT, null),
                        new Part(null, new InlineData(contentType, base64Image))))),
                new GenerationConfig("application/json", buildSchema()));

        // 모델명에 URI 템플릿을 쓰면 "://"가 퍼센트 인코딩돼 호스트가 사라진다.
        // 완성된 URI를 직접 만들어 그대로 넘긴다. 키는 쿼리파라미터가 아니라 헤더로 보낸다
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
        } catch (RestClientResponseException e) {
            throw new ReceiptOcrException(
                    "Gemini 영수증 인식 호출에 실패했습니다. status = " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new ReceiptOcrException("Gemini 영수증 인식 호출에 실패했습니다.", e);
        }

        return parseReceiptJson(extractJsonText(response));
    }

    private ResponseSchema buildSchema() {
        return new ResponseSchema("OBJECT", new SchemaProperties(
                new SchemaProperty("STRING", true),
                new SchemaProperty("STRING", true),
                new SchemaProperty("INTEGER", true)));
    }

    private String extractJsonText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ReceiptOcrException("Gemini 응답에 candidates가 없습니다.");
        }
        ResponseContent content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new ReceiptOcrException("Gemini 응답에 parts가 없습니다.");
        }
        String text = content.parts().get(0).text();
        if (text == null || text.isBlank()) {
            // 안전 필터 등으로 text 없는 part가 올 수 있다 — 빈 응답을 성공으로 다루지 않는다
            throw new ReceiptOcrException("Gemini 응답에 text가 없습니다.");
        }
        return text;
    }

    // 네트워크 없이도 단독으로 테스트할 수 있도록 패키지 접근으로 뺀다
    ReceiptOcrResult parseReceiptJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ReceiptOcrResult.class);
        } catch (JsonProcessingException e) {
            throw new ReceiptOcrException("Gemini 응답 JSON 파싱에 실패했습니다.", e);
        }
    }

    private record GenerateContentRequest(List<RequestContent> contents, GenerationConfig generationConfig) {
    }

    private record RequestContent(List<Part> parts) {
    }

    // text와 inlineData는 하나만 채워 보낸다 — 둘 다 보내면 Gemini가 어느 쪽을 쓸지 모호해진다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Part(String text, InlineData inlineData) {
    }

    private record InlineData(String mimeType, String data) {
    }

    private record GenerationConfig(String responseMimeType, ResponseSchema responseSchema) {
    }

    private record ResponseSchema(String type, SchemaProperties properties) {
    }

    private record SchemaProperties(SchemaProperty storeName, SchemaProperty transactedAt, SchemaProperty totalAmount) {
    }

    private record SchemaProperty(String type, boolean nullable) {
    }

    private record GenerateContentResponse(List<Candidate> candidates) {
    }

    private record Candidate(ResponseContent content) {
    }

    private record ResponseContent(List<ResponsePart> parts) {
    }

    private record ResponsePart(String text) {
    }
}