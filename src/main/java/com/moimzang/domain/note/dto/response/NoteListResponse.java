package com.moimzang.domain.note.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NoteListResponse {

    @Schema(description = "메모 목록(최신 작성순)")
    private final List<NoteResponse> notes;

    @Schema(description = "다음 페이지 cursor. 다음 페이지가 없으면 null", example = "MjAyNi0xMS0wN1QxMDozMA.12")
    private final String nextCursor;
}
