package com.moimzang.global.ai;

// 영수증 이미지 인식 호출 전담. AiClient와 별도 인터페이스다 — AiClient.generateContent는
// 텍스트만 받아 멀티모달(이미지) 입력을 표현할 수 없다. 실패는 ReceiptOcrException으로
// 통일해서 올리고, 도메인별 ErrorCode 변환은 호출하는 쪽의 책임이다
public interface ReceiptOcrClient {

    ReceiptOcrResult recognize(byte[] imageBytes, String contentType);
}