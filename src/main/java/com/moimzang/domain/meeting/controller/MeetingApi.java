package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "회의", description = "회의·안건 관리 API")
public interface MeetingApi {

    @Operation(summary = "회의 만들기", description = "제목·날짜·카테고리·안건 목록을 받아 회의와 안건을 한 번에 생성한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND 또는 MEETING_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingCreateResponse>> createMeeting(
            Long termId, @Valid MeetingCreateRequest request);
}
