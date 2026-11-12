package com.moimzang.domain.chat.service;

// 도구 호출 반복이 상한을 넘었을 때. AI 호출 실패(AiClientException)와 원인이 달라서
// 로그를 구분하려고 별도 타입으로 둔다 — 응답은 둘 다 CHAT_502_AI_FAILED로 같다
public class ChatToolLoopExceededException extends RuntimeException {

    public ChatToolLoopExceededException(String message) {
        super(message);
    }
}
