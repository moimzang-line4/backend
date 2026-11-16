package com.moimzang.domain.meeting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingCreateRequest {

    @Schema(description = "회의 제목", example = "가을 정기공연 운영진 회의")
    @NotBlank
    @Size(max = 100)
    private String title;

    @Schema(description = "회의 날짜", example = "2026-11-07")
    @NotNull
    private LocalDate meetingDate;

    @Schema(description = "회의 카테고리 ID(일정 카테고리와 별도 운영)", example = "1")
    @NotNull
    private Long categoryId;

    @Schema(description = "소속 일정 ID(선택). 일정 상세의 '회의록 작성하기'로 만들 때만 보낸다 — "
            + "보내면 이 회의의 안건 전체가 그 일정에 속하고 안건 연결 후보에서 빠진다",
            example = "1")
    private Long scheduleId;

    @Schema(description = "참석자(자유 텍스트, 선택)", example = "김가은, 홍보팀")
    @Size(max = 500)
    private String attendees;


    @Schema(description = "안건 내용 목록. 등록 순서가 그대로 안건 순서(orderNo)가 된다",
            example = "[\"관객 참여 이벤트\", \"공연 홍보 계획\"]")
    @NotEmpty
    private List<@NotBlank @Size(max = 100) String> agendas;
}
