package com.moimzang.domain.note.dto.request;

import com.moimzang.domain.note.enums.NoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 수정하는 부분 수정 요청이라 전부 선택값이다 — null이면 그 필드는 바뀌지 않는다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NoteUpdateRequest {

    @Schema(description = "메모 유형(선택)", example = "DECISION")
    private NoteType type;

    @Schema(description = "제목(선택)", example = "공연 포스터 인쇄")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String title;

    @Schema(description = "담당자(선택). 빈 문자열을 보내면 담당자를 지운다", example = "김가은")
    @Size(max = 50)
    private String assignee;

    @Schema(description = "내용(선택, 100자 이하)", example = "A2 사이즈 30장, 금요일까지")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String content;
}
