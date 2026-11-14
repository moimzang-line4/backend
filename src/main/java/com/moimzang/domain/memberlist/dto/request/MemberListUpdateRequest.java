package com.moimzang.domain.memberlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 수정하는 부분 수정 요청이라 전부 선택값이다 — null이면 그 항목은 바뀌지 않는다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListUpdateRequest {

    @Schema(description = "명단방 이름(선택)", example = "2026년 2학기 음샘")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String name;

    @Schema(description = "등록 마감 일시(선택)", example = "2026-12-05T23:59:00")
    private LocalDateTime registerDeadline;

    @Schema(description = "학교 수집(선택)", example = "true")
    private Boolean collectSchool;

    @Schema(description = "학번 수집(선택)", example = "true")
    private Boolean collectStudentId;

    @Schema(description = "파트 수집(선택)", example = "true")
    private Boolean collectPart;

    @Schema(description = "학년 수집(선택)", example = "true")
    private Boolean collectGrade;

    @Schema(description = "전화번호 수집(선택)", example = "true")
    private Boolean collectPhone;
}
