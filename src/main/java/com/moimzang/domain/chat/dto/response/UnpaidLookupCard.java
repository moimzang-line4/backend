package com.moimzang.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// '미입금자 조회' 결과 카드. 금액·인원은 백엔드 집계값을 그대로 담고,
// attendeeId는 카드의 '입금 확인' 버튼이 입금 확인 API를 호출할 때 쓴다
public record UnpaidLookupCard(
        @Schema(description = "카드 제목", example = "가을공연·미입금") String title,
        @Schema(description = "미입금 총액(대상자별 미납 금액의 합)", example = "100000") int totalAmount,
        @Schema(description = "미입금 인원", example = "2") int count,
        @Schema(description = "미입금 대상자") List<Item> items
) {

    public record Item(
            @Schema(description = "참가자 ID", example = "401") Long attendeeId,
            @Schema(description = "이름", example = "강민준") String name,
            @Schema(description = "학과·소속", example = "광고홍보학과") String affiliation,
            @Schema(description = "전화번호 뒤 4자리", example = "1140") String phoneLast4,
            @Schema(description = "아직 내지 않은 금액(납부한 차수는 제외)", example = "70000") int amount
    ) {}
}
