package com.moimzang.domain.chat.service;

import com.moimzang.domain.chat.service.tool.ChatToolRegistry;
import com.moimzang.global.ai.AiChatMessage;
import com.moimzang.global.ai.AiChatResult;
import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모델이 도구를 다 쓸 때까지 "호출 → 실행 → 결과 전달"을 반복한다.
 *
 * "가을공연 2차 미입금자" 같은 질문은 한 번에 끝나지 않는다 — 먼저 일정을 찾아 ID를 얻고,
 * 그 ID로 정산을 조회해야 하기 때문에 여러 턴이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatToolLoop {

    // 무한 루프와 토큰 폭증을 막는 상한. 2단 연쇄가 대부분이라 5면 충분하다
    private static final int MAX_ROUNDS = 5;

    private final AiClient aiClient;
    private final ChatToolRegistry toolRegistry;

    public Outcome run(Long termId, List<AiChatMessage> initialMessages) {
        List<AiChatMessage> messages = new ArrayList<>(initialMessages);
        // 어떤 도구가 어떤 결과를 냈는지 호출한 쪽이 알아야 카드를 만들 수 있다.
        // 같은 도구를 여러 번 부르면 마지막 결과를 남긴다
        Map<String, ToolRun> toolRuns = new LinkedHashMap<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            AiChatResult result = aiClient.chat(messages, toolRegistry.definitions());

            if (!result.hasToolCalls()) {
                log.info("[AI 비서] 답변을 생성했습니다. termId = {}, rounds = {}, usedTools = {}",
                        termId, round, toolRuns.keySet());
                return new Outcome(result.content(), toolRuns);
            }

            messages.add(AiChatMessage.assistantToolCalls(result.toolCalls()));
            for (AiToolCall call : result.toolCalls()) {
                String output = toolRegistry.execute(termId, call.name(), call.argumentsJson());
                // 등록된 도구가 아니면 실행 결과가 아니라 에러 JSON이다 — 카드 재료로 쓰면 안 된다
                if (toolRegistry.contains(call.name())) {
                    toolRuns.put(call.name(), new ToolRun(call.argumentsJson(), output));
                }
                messages.add(AiChatMessage.toolResult(call.id(), output));
            }
        }

        // 상한까지 돌았는데도 답을 못 냈다 — 더 돌려봐야 토큰만 쓴다
        log.warn("[AI 비서] 도구 호출이 상한을 넘었습니다. termId = {}, maxRounds = {}, usedTools = {}",
                termId, MAX_ROUNDS, toolRuns.keySet());
        throw new ChatToolLoopExceededException("도구 호출이 " + MAX_ROUNDS + "회를 넘었습니다.");
    }

    // 최종 답변과, 그 과정에서 실행된 도구들의 결과
    public record Outcome(String content, Map<String, ToolRun> toolRuns) {

        public ToolRun toolRun(String toolName) {
            return toolRuns.get(toolName);
        }
    }

    public record ToolRun(String argumentsJson, String resultJson) {}
}
