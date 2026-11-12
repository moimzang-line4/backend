package com.moimzang.domain.chat.service.tool;

// 모델이 넘긴 인자가 잘못됐을 때 쓴다(필수값 누락, 타입 불일치 등).
// 이 예외만 AI에게 되돌려줘 재시도하게 하고, 그 밖의 런타임 예외는 우리 쪽 장애이므로
// 삼키지 않고 그대로 올린다 — 재시도해도 고쳐지지 않는데 토큰만 태우고 장애도 묻히기 때문이다
public class ChatToolArgumentException extends RuntimeException {

    public ChatToolArgumentException(String message) {
        super(message);
    }
}
