package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "회의", description = "회의·안건 관리 API")
public interface MeetingApi {

    @Operation(summary = "회의 만들기", description = "제목·날짜·카테고리·안건 목록을 받아 회의와 안건을 한 번에 생성한다.")
    ResponseEntity<BaseResponse<MeetingCreateResponse>> createMeeting(
            Long termId, @Valid MeetingCreateRequest request);
}
