package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moimzang.domain.settlement.service.OpenAiReceiptOcrClient.ChatCompletionResponse;
import com.moimzang.domain.settlement.service.OpenAiReceiptOcrClient.Choice;
import com.moimzang.domain.settlement.service.OpenAiReceiptOcrClient.ResponseMessage;
import com.moimzang.global.ai.ReceiptOcrException;
import com.moimzang.global.ai.ReceiptOcrResult;
import com.moimzang.global.ai.openai.OpenAiProperties;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

class OpenAiReceiptOcrClientTest {

    private final OpenAiProperties properties = new OpenAiProperties(
            "test-api-key", "gpt-4o-mini", "https://api.openai.com", 10000, 30000);
    private final ReceiptOcrOpenAiProperties ocrProperties = new ReceiptOcrOpenAiProperties(
            "gpt-4o-mini", "https://api.openai.com", 10000, 30000);
    private final OpenAiReceiptOcrClient client = new OpenAiReceiptOcrClient(properties, ocrProperties);

    @Test
    void parseReceiptJson_정상_JSON이면_필드를_그대로_채운다() {
        String json = """
                {"storeName": "스타벅스 신촌점", "transactedAt": "2026-11-13T19:00:00", "totalAmount": 50000}
                """;

        ReceiptOcrResult result = client.parseReceiptJson(json);

        assertThat(result.storeName()).isEqualTo("스타벅스 신촌점");
        assertThat(result.transactedAt()).isEqualTo("2026-11-13T19:00:00");
        assertThat(result.totalAmount()).isEqualTo(50000);
    }

    @Test
    void parseReceiptJson_일부_필드가_null이어도_파싱된다() {
        String json = """
                {"storeName": null, "transactedAt": "2026-11-13T19:00:00", "totalAmount": null}
                """;

        ReceiptOcrResult result = client.parseReceiptJson(json);

        assertThat(result.storeName()).isNull();
        assertThat(result.transactedAt()).isEqualTo("2026-11-13T19:00:00");
        assertThat(result.totalAmount()).isNull();
    }

    @Test
    void parseReceiptJson_깨진_JSON이면_ReceiptOcrException을_던진다() {
        String brokenJson = "{storeName: 스타벅스,,,";

        assertThatThrownBy(() -> client.parseReceiptJson(brokenJson))
                .isInstanceOf(ReceiptOcrException.class);
    }

    @Test
    void extractContent_choices가_비어있으면_ReceiptOcrException을_던진다() {
        ChatCompletionResponse response = new ChatCompletionResponse(List.of());

        assertThatThrownBy(() -> client.extractContent(response))
                .isInstanceOf(ReceiptOcrException.class);
    }

    @Test
    void extractContent_finishReason이_stop이_아니면_ReceiptOcrException을_던진다() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new Choice(new ResponseMessage("{}", null), "length")));

        assertThatThrownBy(() -> client.extractContent(response))
                .isInstanceOf(ReceiptOcrException.class);
    }

    @Test
    void extractContent_refusal이_있으면_ReceiptOcrException을_던진다() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new Choice(new ResponseMessage(null, "이 요청은 처리할 수 없습니다."), "stop")));

        assertThatThrownBy(() -> client.extractContent(response))
                .isInstanceOf(ReceiptOcrException.class);
    }

    @Test
    void extractContent_content가_비어있으면_ReceiptOcrException을_던진다() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new Choice(new ResponseMessage("", null), "stop")));

        assertThatThrownBy(() -> client.extractContent(response))
                .isInstanceOf(ReceiptOcrException.class);
    }

    @Test
    void extractContent_정상_응답이면_content를_그대로_돌려준다() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new Choice(new ResponseMessage("{\"storeName\":null}", null), "stop")));

        assertThat(client.extractContent(response)).isEqualTo("{\"storeName\":null}");
    }

    // apiKey가 비어 있으면 RestClient를 호출하기 전에 끊어낸다 — 네트워크 없이도 이 경로를
    // 그대로 검증할 수 있다(생성자에서는 검사하지 않는다 — 키가 없어도 기동은 성공해야 한다)
    @Test
    void recognize_apiKey가_비어있으면_네트워크_호출_없이_ReceiptOcrException을_던진다() {
        OpenAiProperties blankKeyProperties = new OpenAiProperties(
                "", "gpt-4o-mini", "https://api.openai.com", 10000, 30000);
        OpenAiReceiptOcrClient clientWithoutKey = new OpenAiReceiptOcrClient(blankKeyProperties, ocrProperties);

        assertThatThrownBy(() -> clientWithoutKey.recognize(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(ReceiptOcrException.class);
    }

    // 요청 생성 단계에서 IOException을 던지는 가짜 RestClient를 주입해, 실제 OpenAI를 호출하지
    // 않고도 네트워크 호출 실패 경로(RestClientException catch)를 검증한다
    @Test
    void recognize_호출_자체가_실패하면_ReceiptOcrException을_던진다() {
        RestClient failingRestClient = RestClient.builder()
                .requestFactory((URI uri, HttpMethod method) -> {
                    throw new IOException("연결할 수 없습니다.");
                })
                .build();
        OpenAiReceiptOcrClient clientWithFailingCall =
                new OpenAiReceiptOcrClient(properties, ocrProperties, failingRestClient);

        assertThatThrownBy(() -> clientWithFailingCall.recognize(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(ReceiptOcrException.class);
    }
}
