package com.moimzang.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomCreateRequest {

    @Schema(description = "대화방 제목(선택). 비우면 첫 질문을 보낼 때 그 질문 텍스트로 채워진다",
            example = "가을공연 미입금자 확인")
    @Size(max = 100)
    private String title;
}
