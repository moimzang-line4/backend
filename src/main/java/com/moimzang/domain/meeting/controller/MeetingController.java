package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingUpdateResponse;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.service.MeetingService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    // 기수의 회의 목록을 상태·카테고리·검색어로 필터링해 cursor 페이지네이션으로 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/meetings")
    public ResponseEntity<BaseResponse<MeetingListResponse>> listMeetings(
            @PathVariable Long termId,
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        MeetingListResponse response = meetingService.listMeetings(termId, status, categoryId, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 회의 상세(안건 목록 + 진행률)를 조회한다
    @Override
    @GetMapping("/api/meetings/{meetingId}")
    public ResponseEntity<BaseResponse<MeetingDetailResponse>> getMeetingDetail(@PathVariable Long meetingId) {
        MeetingDetailResponse response = meetingService.getMeetingDetail(meetingId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 회의 정보를 부분 수정한다(안건은 별도 API)
    @Override
    @PatchMapping("/api/meetings/{meetingId}")
    public ResponseEntity<BaseResponse<MeetingUpdateResponse>> updateMeeting(
            @PathVariable Long meetingId,
            @Valid @RequestBody MeetingUpdateRequest request
    ) {
        MeetingUpdateResponse response = meetingService.updateMeeting(meetingId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 회의를 삭제한다(상태 무관 허용)
    @Override
    @DeleteMapping("/api/meetings/{meetingId}")
    public ResponseEntity<BaseResponse<Void>> deleteMeeting(@PathVariable Long meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 기수의 회의 카테고리 목록을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/meeting-categories")
    public ResponseEntity<BaseResponse<MeetingCategoryListResponse>> listCategories(@PathVariable Long termId) {
        MeetingCategoryListResponse response = meetingService.listCategories(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수에 새 회의 카테고리를 생성한다
    @Override
    @PostMapping("/api/terms/{termId}/meeting-categories")
    public ResponseEntity<BaseResponse<MeetingCategoryResponse>> createCategory(
            @PathVariable Long termId,
            @Valid @RequestBody MeetingCategoryCreateRequest request
    ) {
        MeetingCategoryResponse response = meetingService.createCategory(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
