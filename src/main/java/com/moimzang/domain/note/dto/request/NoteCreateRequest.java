package com.moimzang.domain.note.dto.request;

import com.moimzang.domain.note.enums.NoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class NoteCreateRequest {

    @Schema(description = "메모 유형", example = "TODO")
    @NotNull
    private NoteType type;

    @Schema(description = "제목", example = "공연 포스터 인쇄")
    @NotBlank
    @Size(max = 100)
    private String title;

    @Schema(description = "담당자(선택)", example = "김가은, 홍보팀")
    @Size(max = 50)
    private String assignee;

    @Schema(description = "내용(100자 이하)", example = "A2 사이즈 30장, 금요일까지")
    @NotBlank
    @Size(max = 100)
    private String content;
}
