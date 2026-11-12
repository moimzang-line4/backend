package com.moimzang.domain.chat.service.tool;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 등록된 도구를 이름으로 찾아 실행한다. 스프링이 ChatTool 구현체를 전부 주입해주므로
// 도구를 추가할 때 이 클래스를 고칠 필요가 없다
@Slf4j
@Component
public class ChatToolRegistry {

    private final Map<String, ChatTool> tools;
    private final ObjectMapper objectMapper;

    public ChatToolRegistry(List<ChatTool> chatTools, ObjectMapper objectMapper) {
        this.tools = chatTools.stream()
                .collect(Collectors.toMap(tool -> tool.definition().name(), Function.identity()));
        this.objectMapper = objectMapper;
    }

    public List<AiTool> definitions() {
        return tools.values().stream().map(ChatTool::definition).toList();
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * 도구를 실행하고 결과를 AI에게 돌려줄 JSON 문자열로 만든다.
     *
     * AI가 고칠 수 있는 실수(없는 도구 이름, 인자 JSON 파싱 실패, 타입 불일치)는 예외로 올리지 않고
     * error JSON을 돌려줘서 다시 부를 기회를 준다. 반면 DB 장애나 우리 코드 버그는 재시도해도
     * 고쳐지지 않고 묻히면 장애를 놓치므로 그대로 올려 상위에서 502로 끊게 한다.
     */
    public String execute(Long termId, String toolName, String argumentsJson) {
        ChatTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("[AI 도구 실행] 등록되지 않은 도구입니다. termId = {}, toolName = {}", termId, toolName);
            return errorJson("'" + toolName + "'은(는) 사용할 수 없는 도구입니다.",
                    "사용 가능한 도구: " + String.join(", ", tools.keySet()));
        }

        JsonNode args;
        try {
            args = (argumentsJson == null || argumentsJson.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
        } catch (JacksonException e) {
            log.warn("[AI 도구 실행] 인자 JSON을 파싱하지 못했습니다. termId = {}, toolName = {}", termId, toolName);
            return errorJson("인자가 올바른 JSON이 아닙니다.", "도구 스키마에 맞는 JSON으로 다시 호출하세요.");
        }

        long startedAt = System.currentTimeMillis();
        Object result;
        try {
            result = tool.execute(termId, args);
        } catch (ChatToolArgumentException e) {
            log.warn("[AI 도구 실행] 인자가 올바르지 않습니다. termId = {}, toolName = {}, reason = {}",
                    termId, toolName, e.getMessage());
            return errorJson(e.getMessage(), "인자를 고쳐서 다시 호출하세요.");
        }

        log.info("[AI 도구 실행] 실행을 완료했습니다. termId = {}, toolName = {}, elapsedMs = {}",
                termId, toolName, System.currentTimeMillis() - startedAt);

        return writeJson(result);
    }

    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException e) {
            // 우리가 만든 DTO를 직렬화하지 못하는 건 코드 버그다 — AI에게 떠넘기지 않고 올린다
            throw new IllegalStateException("도구 결과를 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    private String errorJson(String error, String hint) {
        return writeJson(Map.of("error", error, "hint", hint));
    }
}
