package com.moimzang.domain.meeting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 AI 요약을 직접 고친 뒤 "회의록 저장하기"로 확정할 때 보내는 요청이다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingSummaryUpdateRequest {

    @Schema(description = "회의 전체 요약(사용자가 수정한 내용)", example = "가을 정기공연 준비를 위해 관객 참여 이벤트, 공연 홍보 일정, 장비 점검 계획을 논의했어요.")
    @NotBlank
    private String summaryContent;
}
