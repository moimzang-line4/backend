package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.service.MeetingService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeetingController implements MeetingApi {

    private final MeetingService meetingService;

    // 기수의 회의를 안건과 함께 한 번에 생성한다
    @Override
    @PostMapping("/api/terms/{termId}/meetings")
    public ResponseEntity<BaseResponse<MeetingCreateResponse>> createMeeting(
            @PathVariable Long termId,
            @Valid @RequestBody MeetingCreateRequest request
    ) {
        MeetingCreateResponse response = meetingService.createMeeting(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
