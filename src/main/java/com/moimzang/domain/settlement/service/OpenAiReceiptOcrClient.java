
package com.moimzang.domain.settlement.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moimzang.global.ai.ReceiptOcrClient;
import com.moimzang.global.ai.ReceiptOcrException;
import com.moimzang.global.ai.ReceiptOcrResult;
import com.moimzang.global.ai.openai.OpenAiProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// OpenAI Chat Completions(비전 입력)으로 영수증 이미지를 분석한다. app.ai.provider와 무관하게
// 항상 등록한다 — 영수증 인식은 벤더 전환 대상이 아니고(decisions #69/#74), 키가 없어도 앱
// 기동이 막히면 안 되므로(다른 팀원 로컬 실행·CI) 생성자가 아니라 호출 시점에 키를 검사한다
@Component
public class OpenAiReceiptOcrClient implements ReceiptOcrClient {

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

    private final OpenAiProperties openAiProperties;
    private final ReceiptOcrOpenAiProperties ocrProperties;
    private final RestClient restClient;

    @Autowired
    public OpenAiReceiptOcrClient(OpenAiProperties openAiProperties, ReceiptOcrOpenAiProperties ocrProperties) {
        this(openAiProperties, ocrProperties, buildRestClient(ocrProperties));
    }

    // 테스트에서 실패하는 RestClient(또는 요청 팩토리)를 주입해 실제 네트워크 없이
    // 호출 실패 경로를 검증할 수 있도록 패키지 접근 생성자를 따로 둔다
    OpenAiReceiptOcrClient(OpenAiProperties openAiProperties, ReceiptOcrOpenAiProperties ocrProperties,
                            RestClient restClient) {
        this.openAiProperties = openAiProperties;
        this.ocrProperties = ocrProperties;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(ReceiptOcrOpenAiProperties properties) {
        // 타임아웃 없이 두면 OpenAI가 멈췄을 때 호출 스레드가 무한정 블록된다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ReceiptOcrResult recognize(byte[] imageBytes, String contentType) {
        String apiKey = openAiProperties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReceiptOcrException("app.ai.openai.api-key가 설정되지 않았습니다.");
        }

        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        ChatCompletionRequest request = new ChatCompletionRequest(
                ocrProperties.model(),
                List.of(RequestMessage.user(List.of(ContentPart.text(PROMPT), ContentPart.imageUrl(dataUrl)))),
                buildResponseFormat());

        URI uri = URI.create(ocrProperties.baseUrl() + "/v1/chat/completions");

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException e) {
            // 상태 코드를 메시지에 남긴다 — 429(요청 한도)와 401(키 문제)은 대응이 전혀 달라서
            // "호출 실패"로만 뭉뚱그리면 운영에서 원인을 못 찾는다. 키는 헤더에 있어 노출되지 않는다
            throw new ReceiptOcrException(
                    "OpenAI 영수증 인식 호출에 실패했습니다. status = " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패 등 응답 자체를 못 받은 경우
            throw new ReceiptOcrException("OpenAI 영수증 인식 호출에 실패했습니다.", e);
        }

        return parseReceiptJson(extractContent(response));
    }

    private ResponseFormat buildResponseFormat() {
        SchemaProperty stringOrNull = new SchemaProperty(List.of("string", "null"));
        SchemaProperty integerOrNull = new SchemaProperty(List.of("integer", "null"));
        Schema schema = new Schema("object",
                new SchemaProperties(stringOrNull, stringOrNull, integerOrNull),
                List.of("storeName", "transactedAt", "totalAmount"),
                false);
        return new ResponseFormat("json_schema", new JsonSchema("receipt_ocr_result", true, schema));
    }

    // 네트워크 없이도 단독으로 테스트할 수 있도록 패키지 접근으로 뺀다
    String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ReceiptOcrException("OpenAI 응답에 choices가 없습니다.");
        }
        Choice choice = response.choices().get(0);
        // 길이 제한 등으로 끊긴 응답(length)은 JSON이 완결되지 않았을 수 있어 성공으로 보지 않는다
        if (!"stop".equals(choice.finishReason())) {
            throw new ReceiptOcrException("OpenAI 응답의 finish_reason이 stop이 아닙니다: " + choice.finishReason());
        }
        ResponseMessage message = choice.message();
        if (message == null) {
            throw new ReceiptOcrException("OpenAI 응답에 message가 없습니다.");
        }
        if (message.refusal() != null && !message.refusal().isBlank()) {
            // 안전 정책으로 OpenAI가 응답을 거부하면 content 대신 refusal이 채워져서 온다
            throw new ReceiptOcrException("OpenAI가 응답을 거부했습니다: " + message.refusal());
        }
        if (message.content() == null || message.content().isBlank()) {
            throw new ReceiptOcrException("OpenAI 응답에 content가 없습니다.");
        }
        return message.content();
    }

    // 네트워크 없이도 단독으로 테스트할 수 있도록 패키지 접근으로 뺀다
    ReceiptOcrResult parseReceiptJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ReceiptOcrResult.class);
        } catch (JsonProcessingException e) {
            throw new ReceiptOcrException("OpenAI 응답 JSON 파싱에 실패했습니다.", e);
        }
    }

    // OpenAI는 snake_case를 쓰는데 이 프로젝트엔 Jackson 네이밍 전략 설정이 없어서(기본 camelCase)
    // 어긋나는 필드마다 @JsonProperty로 직접 맞춘다
    private record ChatCompletionRequest(String model, List<RequestMessage> messages,
                                          @JsonProperty("response_format") ResponseFormat responseFormat) {}

    private record RequestMessage(String role, List<ContentPart> content) {
        static RequestMessage user(List<ContentPart> content) {
            return new RequestMessage("user", content);
        }
    }

    // text와 imageUrl은 하나만 채워 보낸다 — 타입(type)으로 어느 쪽인지 구분한다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ContentPart(String type, String text, @JsonProperty("image_url") ImageUrl imageUrl) {
        static ContentPart text(String text) {
            return new ContentPart("text", text, null);
        }

        static ContentPart imageUrl(String dataUrl) {
            return new ContentPart("image_url", null, new ImageUrl(dataUrl));
        }
    }

    private record ImageUrl(String url) {}

    private record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {}

    private record JsonSchema(String name, boolean strict, Schema schema) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Schema(String type, SchemaProperties properties, List<String> required,
                           @JsonProperty("additionalProperties") boolean additionalProperties) {}

    private record SchemaProperties(SchemaProperty storeName, SchemaProperty transactedAt,
                                     SchemaProperty totalAmount) {}

    private record SchemaProperty(List<String> type) {}

    // 테스트에서 malformed 응답(choices 비어있음, finish_reason 이상, refusal 등)을 직접
    // 구성할 수 있어야 해서 응답 쪽 레코드는 패키지 접근으로 둔다
    record ChatCompletionResponse(List<Choice> choices) {}

    record Choice(ResponseMessage message, @JsonProperty("finish_reason") String finishReason) {}

    record ResponseMessage(String content, String refusal) {}
}
