package com.moimzang.domain.chat.service;

import com.moimzang.global.ai.AiClient;
import com.moimzang.global.text.SingleLineText;
import com.moimzang.global.ai.AiClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 대화방의 첫 질문으로 목록에 보일 제목을 짓는다.
 *
 * 제목은 부수적인 값이라 생성에 실패해도 답변을 막지 않는다 — 질문 원문으로 대체하고 넘어간다.
 * 다만 연속 실패를 놓치지 않도록 경고 로그는 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomTitleGenerator {

    // ai_chat_room.title 컬럼 길이
    private static final int MAX_LENGTH = 100;
    private static final String FALLBACK = "새 대화";

    private static final String PROMPT = """
            다음은 동아리 운영 비서에게 보낸 첫 질문이다. 이 대화를 목록에서 구분할 수 있도록
            20자 이내의 한국어 명사형 제목으로 바꿔라.
            제목만 출력한다. 따옴표, 마침표, 마크다운, "제목:" 같은 접두어를 붙이지 않는다.

            질문: %s
            """;

    private final AiClient aiClient;

    public String generate(String firstQuestion) {
        try {
            String generated = aiClient.generateContent(PROMPT.formatted(firstQuestion));
            String cleaned = clean(generated);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
            log.warn("[AI 대화방 제목] 생성 결과가 비어 질문 원문을 사용합니다.");
        } catch (AiClientException e) {
            log.warn("[AI 대화방 제목] 생성에 실패해 질문 원문을 사용합니다.", e);
        }
        return fallbackFrom(firstQuestion);
    }

    // 프롬프트로 금지해도 모델이 따옴표나 접두어를 붙여 올 때가 있어 서버에서 한 번 더 정리한다
    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = SingleLineText.normalize(raw);
        text = text.replaceFirst("^(제목|Title)\\s*[:：]\\s*", "");
        text = text.replaceAll("^[\"'“”‘’`*]+", "").replaceAll("[\"'“”‘’`*]+$", "");
        text = text.replaceAll("[.。]+$", "").trim();
        return truncate(text);
    }

    private String fallbackFrom(String firstQuestion) {
        // 질문 원문을 제목으로 쓸 때도 한 줄로 편다 — 여러 줄 질문이 그대로 제목이 되면 목록이 깨진다
        String text = firstQuestion == null ? "" : SingleLineText.normalize(firstQuestion);
        return text.isEmpty() ? FALLBACK : truncate(text);
    }

    private String truncate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
