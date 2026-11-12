package com.moimzang.domain.chat.service.tool;

import tools.jackson.databind.JsonNode;

// 모델이 만든 인자 JSON에서 값을 꺼낸다.
// 모델은 숫자를 문자열로 주거나("10"), 빈 문자열을 null 대신 넣는 등 스키마를 어기는 일이 잦아
// 도구마다 같은 방어 코드를 반복하지 않도록 여기에 모았다
public final class ChatToolArgs {

    private ChatToolArgs() {
    }

    public static Long requiredLong(JsonNode args, String field) {
        Long value = optionalLong(args, field);
        if (value == null) {
            throw new ChatToolArgumentException(field + "은(는) 필수입니다.");
        }
        return value;
    }

    public static Long optionalLong(JsonNode args, String field) {
        JsonNode node = valueNode(args, field);
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException e) {
            throw new ChatToolArgumentException(field + "은(는) 숫자여야 합니다. 받은 값: " + node.asText());
        }
    }

    public static Integer optionalInt(JsonNode args, String field) {
        Long value = optionalLong(args, field);
        return value == null ? null : value.intValue();
    }

    public static String optionalText(JsonNode args, String field) {
        JsonNode node = valueNode(args, field);
        if (node == null) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    // null 노드와 빈 문자열을 "값 없음"으로 통일한다 — 모델이 둘을 섞어 쓴다
    private static JsonNode valueNode(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode node = args.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() && node.asText().trim().isEmpty()) {
            return null;
        }
        return node;
    }
}
