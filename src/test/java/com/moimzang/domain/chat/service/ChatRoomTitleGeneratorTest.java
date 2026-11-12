package com.moimzang.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomTitleGeneratorTest {

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private ChatRoomTitleGenerator titleGenerator;

    @Test
    void generate_모델이_만든_제목을_그대로_쓴다() {
        given(aiClient.generateContent(anyString())).willReturn("가을공연 미입금 현황");

        assertThat(titleGenerator.generate("가을공연 미입금자 알려줘")).isEqualTo("가을공연 미입금 현황");
    }

    @Test
    void generate_따옴표와_마침표를_제거한다() {
        given(aiClient.generateContent(anyString())).willReturn("\"가을공연 미입금 현황.\"");

        assertThat(titleGenerator.generate("질문")).isEqualTo("가을공연 미입금 현황");
    }

    @Test
    void generate_제목_접두어를_제거한다() {
        given(aiClient.generateContent(anyString())).willReturn("제목: 가을공연 미입금 현황");

        assertThat(titleGenerator.generate("질문")).isEqualTo("가을공연 미입금 현황");
    }

    @Test
    void generate_앞뒤_공백과_개행을_정리한다() {
        given(aiClient.generateContent(anyString())).willReturn("\n  가을공연 미입금 현황  \n");

        assertThat(titleGenerator.generate("질문")).isEqualTo("가을공연 미입금 현황");
    }

    @Test
    void generate_100자를_넘으면_잘라낸다() {
        given(aiClient.generateContent(anyString())).willReturn("가".repeat(150));

        // title 컬럼이 VARCHAR(100)이라 넘기면 저장에서 터진다
        assertThat(titleGenerator.generate("질문")).hasSize(100);
    }

    @Test
    void generate_AI가_실패하면_질문_원문을_쓴다() {
        given(aiClient.generateContent(anyString())).willThrow(new AiClientException("타임아웃"));

        // 제목 생성 실패가 답변을 막아서는 안 된다
        assertThat(titleGenerator.generate("가을공연 미입금자 알려줘")).isEqualTo("가을공연 미입금자 알려줘");
    }

    @Test
    void generate_AI_응답이_비면_질문_원문을_쓴다() {
        given(aiClient.generateContent(anyString())).willReturn("   ");

        assertThat(titleGenerator.generate("가을공연 미입금자 알려줘")).isEqualTo("가을공연 미입금자 알려줘");
    }

    @Test
    void generate_폴백할_질문_원문도_100자를_넘으면_잘라낸다() {
        given(aiClient.generateContent(anyString())).willThrow(new AiClientException("타임아웃"));

        assertThat(titleGenerator.generate("나".repeat(150))).hasSize(100);
    }

    @Test
    void generate_질문이_비어_있으면_기본값을_쓴다() {
        given(aiClient.generateContent(anyString())).willThrow(new AiClientException("타임아웃"));

        assertThat(titleGenerator.generate("   ")).isEqualTo("새 대화");
    }
}
