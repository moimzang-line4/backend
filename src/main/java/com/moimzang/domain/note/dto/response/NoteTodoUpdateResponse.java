package com.moimzang.domain.note.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NoteTodoUpdateResponse {

    @Schema(description = "메모 ID", example = "1")
    private final Long noteId;

    @Schema(description = "완료 여부", example = "true")
    private final Boolean isTodoDone;
}
