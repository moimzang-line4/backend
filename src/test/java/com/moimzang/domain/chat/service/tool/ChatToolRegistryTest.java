package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void execute_정상_인자면_도구_결과를_JSON으로_돌려준다() {
        ChatToolRegistry registry = registryOf(new EchoTool());

        String result = registry.execute(1L, "echo", "{\"value\":\"안녕\"}");

        assertThat(result).isEqualTo("{\"echoed\":\"안녕\"}");
    }

    @Test
    void execute_등록되지_않은_도구면_예외_대신_error_JSON을_돌려준다() throws Exception {
        ChatToolRegistry registry = registryOf(new EchoTool());

        JsonNode result = objectMapper.readTree(registry.execute(1L, "unknown_tool", "{}"));

        // AI가 "내가 잘못 불렀구나"를 알고 다시 고를 수 있어야 하므로 예외로 끊지 않는다
        assertThat(result.get("error").asText()).contains("unknown_tool");
        assertThat(result.get("hint").asText()).contains("echo");
    }

    @Test
    void execute_인자가_JSON이_아니면_error_JSON을_돌려준다() throws Exception {
        ChatToolRegistry registry = registryOf(new EchoTool());

        JsonNode result = objectMapper.readTree(registry.execute(1L, "echo", "이건 JSON이 아니다"));

        assertThat(result.get("error").asText()).contains("JSON");
    }

    @Test
    void execute_인자_타입이_맞지_않으면_error_JSON을_돌려준다() throws Exception {
        ChatToolRegistry registry = registryOf(new NeedsIdTool());

        JsonNode result = objectMapper.readTree(registry.execute(1L, "needs_id", "{\"scheduleId\":\"열\"}"));

        assertThat(result.get("error").asText()).contains("숫자");
    }

    @Test
    void execute_필수_인자가_없으면_error_JSON을_돌려준다() throws Exception {
        ChatToolRegistry registry = registryOf(new NeedsIdTool());

        JsonNode result = objectMapper.readTree(registry.execute(1L, "needs_id", "{}"));

        assertThat(result.get("error").asText()).contains("필수");
    }

    @Test
    void execute_인자가_비어도_도구를_실행한다() {
        ChatToolRegistry registry = registryOf(new EchoTool());

        String result = registry.execute(1L, "echo", null);

        assertThat(result).isEqualTo("{\"echoed\":null}");
    }

    @Test
    void execute_도구가_던진_런타임_예외는_삼키지_않고_그대로_올린다() {
        ChatToolRegistry registry = registryOf(new BrokenTool());

        // DB 장애·코드 버그는 AI가 재시도해도 못 고친다 — 상위에서 502로 끊어야 한다
        assertThatThrownBy(() -> registry.execute(1L, "broken", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 연결 실패");
    }

    @Test
    void definitions_등록된_모든_도구의_선언을_돌려준다() {
        ChatToolRegistry registry = registryOf(new EchoTool(), new NeedsIdTool());

        assertThat(registry.definitions()).extracting(AiTool::name)
                .containsExactlyInAnyOrder("echo", "needs_id");
    }

    private ChatToolRegistry registryOf(ChatTool... tools) {
        return new ChatToolRegistry(List.of(tools), objectMapper);
    }

    private static class EchoTool implements ChatTool {

        @Override
        public AiTool definition() {
            return new AiTool("echo", "받은 값을 그대로 돌려준다", Map.of());
        }

        @Override
        public Object execute(Long termId, JsonNode args) {
            return new EchoResult(ChatToolArgs.optionalText(args, "value"));
        }

        // Map.of는 null 값을 담지 못해 "인자 없음" 케이스를 표현할 수 없다
        private record EchoResult(String echoed) {}
    }

    private static class NeedsIdTool implements ChatTool {

        @Override
        public AiTool definition() {
            return new AiTool("needs_id", "scheduleId가 필요한 도구", Map.of());
        }

        @Override
        public Object execute(Long termId, JsonNode args) {
            return Map.of("scheduleId", ChatToolArgs.requiredLong(args, "scheduleId"));
        }
    }

    private static class BrokenTool implements ChatTool {

        @Override
        public AiTool definition() {
            return new AiTool("broken", "항상 실패하는 도구", Map.of());
        }

        @Override
        public Object execute(Long termId, JsonNode args) {
            throw new IllegalStateException("DB 연결 실패");
        }
    }
}
