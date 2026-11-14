package com.moimzang.domain.note.controller;

import com.moimzang.domain.note.dto.request.NoteCreateRequest;
import com.moimzang.domain.note.dto.request.NoteUpdateRequest;
import com.moimzang.domain.note.dto.response.NoteCreateResponse;
import com.moimzang.domain.note.dto.response.NoteUpdateResponse;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "운영 메모", description = "운영 메모·할 일 관리 API")
public interface NoteApi {

    @Operation(summary = "운영 메모 작성",
               description = "일정 운영 공간에 메모를 작성한다. 할 일(TODO)은 미완료 상태로 만들어진다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "작성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(필수 항목 누락·길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "NOTE_404_SCHEDULE_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<NoteCreateResponse>> createNote(
            @Parameter(description = "일정 ID", example = "10") Long scheduleId, @Valid NoteCreateRequest request);

    @Operation(summary = "운영 메모 수정",
               description = "보낸 필드만 수정한다. 유형을 할 일로 바꾸면 미완료로, 할 일에서 다른 유형으로 바꾸면 완료 여부가 비워진다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(공백만 입력·길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "NOTE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<NoteUpdateResponse>> updateNote(
            @Parameter(description = "메모 ID", example = "1") Long noteId, @Valid NoteUpdateRequest request);

    @Operation(summary = "운영 메모 삭제", description = "메모를 삭제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "NOTE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteNote(@Parameter(description = "메모 ID", example = "1") Long noteId);
}
