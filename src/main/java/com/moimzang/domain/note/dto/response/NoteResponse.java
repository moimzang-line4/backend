package com.moimzang.domain.note.dto.response;

import com.moimzang.domain.note.enums.NoteType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NoteResponse {

    @Schema(description = "메모 ID", example = "1")
    private final Long noteId;

    @Schema(description = "메모 유형", example = "TODO")
    private final NoteType type;

    @Schema(description = "제목", example = "공연 포스터 인쇄")
    private final String title;

    @Schema(description = "담당자. 없으면 null", example = "김가은")
    private final String assignee;

    @Schema(description = "내용", example = "A2 사이즈 30장, 금요일까지")
    private final String content;

    @Schema(description = "할 일 완료 여부. 할 일(TODO)이 아니면 null", example = "false")
    private final Boolean isTodoDone;

    @Schema(description = "작성 시각", example = "2026-10-29T14:00:00")
    private final LocalDateTime createdAt;

    @Schema(description = "수정 시각", example = "2026-11-07T10:30:00")
    private final LocalDateTime updatedAt;
}
