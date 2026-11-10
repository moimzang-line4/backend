package com.moimzang.domain.meeting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 수정하는 부분 수정 요청이라 전부 선택값이다 — null이면 해당 필드는 바뀌지 않는다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingUpdateRequest {

    @Schema(description = "회의 제목(선택)", example = "가을 정기공연 운영진 회의")
    @Size(max = 100)
    private String title;

    @Schema(description = "회의 날짜(선택)", example = "2026-11-07")
    private LocalDate meetingDate;

    @Schema(description = "회의 카테고리 ID(선택)", example = "1")
    private Long categoryId;

    @Schema(description = "참석자(선택)", example = "김가은, 홍보팀")
    @Size(max = 500)
    private String attendees;

    @Schema(description = "회의 목록 카드에 표시되는 한 줄 설명(선택)", example = "가을 공연 준비 논의")
    @Size(max = 100)
    private String listSummary;
}
