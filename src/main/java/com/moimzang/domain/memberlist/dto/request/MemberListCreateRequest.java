package com.moimzang.domain.memberlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 수집 토글은 보내지 않으면 켜진 상태로 만든다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListCreateRequest {

    @Schema(description = "명단방 이름", example = "2026년 2학기 음샘")
    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(description = "등록 마감 일시. 이 시각까지 등록을 받는다", example = "2026-11-30T23:59:00")
    @NotNull
    private LocalDateTime registerDeadline;

    @Schema(description = "학교 수집(기본 true)", example = "true")
    private Boolean collectSchool;

    @Schema(description = "학번 수집(기본 true)", example = "true")
    private Boolean collectStudentId;

    @Schema(description = "파트 수집(기본 true)", example = "false")
    private Boolean collectPart;

    @Schema(description = "학년 수집(기본 true)", example = "false")
    private Boolean collectGrade;

    @Schema(description = "전화번호 수집(기본 true)", example = "true")
    private Boolean collectPhone;
}
