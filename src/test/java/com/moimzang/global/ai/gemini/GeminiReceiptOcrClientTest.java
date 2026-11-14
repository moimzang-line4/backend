package com.moimzang.global.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moimzang.global.ai.ReceiptOcrException;
import com.moimzang.global.ai.ReceiptOcrResult;
import org.junit.jupiter.api.Test;

class GeminiReceiptOcrClientTest {

    private final GeminiProperties properties = new GeminiProperties(
            "test-api-key", "gemini-3.8-flash", "https://generativelanguage.googleapis.com", 10000, 30000);
    private final GeminiReceiptOcrClient client = new GeminiReceiptOcrClient(properties);

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

    // apiKey가 비어 있으면 RestClient를 호출하기 전에 끊어낸다 — 네트워크 없이도 이 경로를
    // 그대로 검증할 수 있다(생성자에서는 검사하지 않는다 — 결정사항: 키가 없어도 기동은 성공해야 한다)
    @Test
    void recognize_apiKey가_비어있으면_네트워크_호출_없이_ReceiptOcrException을_던진다() {
        GeminiProperties blankKeyProperties = new GeminiProperties(
                "", "gemini-3.8-flash", "https://generativelanguage.googleapis.com", 10000, 30000);
        GeminiReceiptOcrClient clientWithoutKey = new GeminiReceiptOcrClient(blankKeyProperties);

        assertThatThrownBy(() -> clientWithoutKey.recognize(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(ReceiptOcrException.class);
    }
}
