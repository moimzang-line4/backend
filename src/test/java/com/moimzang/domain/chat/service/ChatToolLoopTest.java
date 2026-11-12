package com.moimzang.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.chat.service.tool.ChatToolRegistry;
import com.moimzang.global.ai.AiChatMessage;
import com.moimzang.global.ai.AiChatResult;
import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.global.ai.AiToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatToolLoopTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private ChatToolRegistry toolRegistry;

    @InjectMocks
    private ChatToolLoop chatToolLoop;

    @Test
    void run_도구를_안_부르면_바로_답변을_돌려준다() {
        given(aiClient.chat(any(), any())).willReturn(new AiChatResult("안녕하세요", null));

        ChatToolLoop.Outcome outcome = chatToolLoop.run(1L, List.of(AiChatMessage.user("안녕")));

        assertThat(outcome.content()).isEqualTo("안녕하세요");
        assertThat(outcome.toolRuns()).isEmpty();
        verify(toolRegistry, never()).execute(anyLong(), anyString(), anyString());
    }

    @Test
    void run_도구_결과를_다시_모델에_넘겨_최종_답변을_받는다() {
        given(aiClient.chat(any(), any()))
                .willReturn(toolCallResult("c1", "get_settlement_status", "{\"scheduleId\":10}"))
                .willReturn(new AiChatResult("미입금자는 2명입니다", null));
        given(toolRegistry.contains("get_settlement_status")).willReturn(true);
        given(toolRegistry.execute(eq(1L), eq("get_settlement_status"), anyString()))
                .willReturn("{\"unpaidCount\":2}");

        ChatToolLoop.Outcome outcome = chatToolLoop.run(1L, List.of(AiChatMessage.user("미입금자 알려줘")));

        assertThat(outcome.content()).isEqualTo("미입금자는 2명입니다");
        assertThat(outcome.toolRun("get_settlement_status").resultJson()).isEqualTo("{\"unpaidCount\":2}");

        // 두 번째 호출에는 도구 호출 턴과 그 결과가 누적돼 있어야 모델이 대화를 이어갈 수 있다
        ArgumentCaptor<List<AiChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient, org.mockito.Mockito.times(2)).chat(captor.capture(), any());
        List<AiChatMessage> secondCall = captor.getAllValues().get(1);
        assertThat(secondCall).hasSize(3);
        assertThat(secondCall.get(1).role()).isEqualTo(AiChatMessage.Role.ASSISTANT);
        assertThat(secondCall.get(2).role()).isEqualTo(AiChatMessage.Role.TOOL);
        assertThat(secondCall.get(2).toolCallId()).isEqualTo("c1");
    }

    @Test
    void run_일정_검색_후_정산_조회로_2단_연쇄를_처리한다() {
        given(aiClient.chat(any(), any()))
                .willReturn(toolCallResult("c1", "search_schedules", "{\"keyword\":\"가을공연\"}"))
                .willReturn(toolCallResult("c2", "get_settlement_status", "{\"scheduleId\":10,\"roundNo\":2}"))
                .willReturn(new AiChatResult("가을공연 2차 미입금자는 1명입니다", null));
        given(toolRegistry.contains(anyString())).willReturn(true);
        given(toolRegistry.execute(eq(1L), eq("search_schedules"), anyString()))
                .willReturn("{\"schedules\":[{\"scheduleId\":10}]}");
        given(toolRegistry.execute(eq(1L), eq("get_settlement_status"), anyString()))
                .willReturn("{\"unpaidCount\":1}");

        ChatToolLoop.Outcome outcome = chatToolLoop.run(1L, List.of(AiChatMessage.user("가을공연 2차 정산 안 한 사람")));

        assertThat(outcome.content()).contains("1명");
        assertThat(outcome.toolRuns()).containsOnlyKeys("search_schedules", "get_settlement_status");
    }

    @Test
    void run_등록되지_않은_도구_결과는_도구_실행_기록에_남기지_않는다() {
        given(aiClient.chat(any(), any()))
                .willReturn(toolCallResult("c1", "unknown_tool", "{}"))
                .willReturn(new AiChatResult("그건 도와드릴 수 없어요", null));
        given(toolRegistry.contains("unknown_tool")).willReturn(false);
        given(toolRegistry.execute(eq(1L), eq("unknown_tool"), anyString()))
                .willReturn("{\"error\":\"사용할 수 없는 도구\"}");

        ChatToolLoop.Outcome outcome = chatToolLoop.run(1L, List.of(AiChatMessage.user("뭐해줘")));

        // 에러 JSON을 카드 재료로 쓰면 안 되므로 실행 기록에서 제외한다
        assertThat(outcome.toolRuns()).isEmpty();
        assertThat(outcome.content()).isEqualTo("그건 도와드릴 수 없어요");
    }

    @Test
    void run_반복_상한을_넘으면_ChatToolLoopExceededException을_던진다() {
        given(aiClient.chat(any(), any()))
                .willReturn(toolCallResult("c1", "search_schedules", "{}"));
        given(toolRegistry.contains(anyString())).willReturn(true);
        given(toolRegistry.execute(anyLong(), anyString(), anyString())).willReturn("{}");

        assertThatThrownBy(() -> chatToolLoop.run(1L, List.of(AiChatMessage.user("무한 질문"))))
                .isInstanceOf(ChatToolLoopExceededException.class);

        verify(aiClient, org.mockito.Mockito.times(5)).chat(any(), any());
    }

    @Test
    void run_AI_호출이_실패하면_AiClientException을_그대로_올린다() {
        given(aiClient.chat(any(), any())).willThrow(new AiClientException("타임아웃"));

        assertThatThrownBy(() -> chatToolLoop.run(1L, List.of(AiChatMessage.user("질문"))))
                .isInstanceOf(AiClientException.class);
    }

    @Test
    void run_도구가_던진_런타임_예외는_그대로_전파한다() {
        given(aiClient.chat(any(), any()))
                .willReturn(toolCallResult("c1", "search_schedules", "{}"));
        given(toolRegistry.execute(anyLong(), anyString(), anyString()))
                .willThrow(new IllegalStateException("DB 연결 실패"));

        assertThatThrownBy(() -> chatToolLoop.run(1L, List.of(AiChatMessage.user("질문"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 연결 실패");
    }

    private AiChatResult toolCallResult(String id, String name, String argumentsJson) {
        return new AiChatResult(null, List.of(new AiToolCall(id, name, argumentsJson)));
    }
}
