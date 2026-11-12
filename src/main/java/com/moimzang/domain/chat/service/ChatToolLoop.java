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
        // 모델이 같은 도구를 여러 번 부를 수 있어(예: "공연"이 여러 일정에 걸릴 때) 전부 모은다
        Map<String, List<ToolRun>> toolRuns = new LinkedHashMap<>();

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
                    toolRuns.computeIfAbsent(call.name(), name -> new ArrayList<>())
                            .add(new ToolRun(call.argumentsJson(), output));
                }
                messages.add(AiChatMessage.toolResult(call.id(), output));
            }
        }

        // 상한까지 돌았는데도 답을 못 냈다 — 더 돌려봐야 토큰만 쓴다
        log.warn("[AI 비서] 도구 호출이 상한을 넘었습니다. termId = {}, maxRounds = {}, usedTools = {}",
                termId, MAX_ROUNDS, toolRuns.keySet());
        throw new ChatToolLoopExceededException("도구 호출이 " + MAX_ROUNDS + "회를 넘었습니다.");
    }

    // 최종 답변과, 그 과정에서 실행된 도구들의 결과(도구별로 실행 순서대로)
    public record Outcome(String content, Map<String, List<ToolRun>> toolRuns) {

        /**
         * 그 도구가 정확히 한 번만 실행됐을 때의 결과. 그 외에는 null이다.
         *
         * 여러 번 불렸다는 건 답변이 여러 대상(예: 일정 2건)을 아우른다는 뜻이라,
         * 하나를 골라 카드로 만들면 나머지를 가린 잘못된 카드가 된다.
         */
        public ToolRun singleRun(String toolName) {
            List<ToolRun> runs = toolRuns.get(toolName);
            return runs != null && runs.size() == 1 ? runs.get(0) : null;
        }

        // 그 도구가 한 번이라도 실행됐는지. 몇 번 불렸든 상관없는 쓰임(가리킬 대상이 없는 화면)에 쓴다
        public boolean hasRun(String toolName) {
            return toolRuns.containsKey(toolName);
        }
    }

    public record ToolRun(String argumentsJson, String resultJson) {}
}
