package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingAgendaCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingSummaryUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingEndResponse;
import com.moimzang.domain.meeting.dto.response.MeetingLinkableAgendaListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryGenerateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryUpdateResponse;
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

    // 기수의 회의 목록을 상태·카테고리·검색어·소속 일정으로 필터링해 cursor 페이지네이션으로 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/meetings")
    public ResponseEntity<BaseResponse<MeetingListResponse>> listMeetings(
            @PathVariable Long termId,
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        MeetingListResponse response =
                meetingService.listMeetings(termId, status, categoryId, keyword, scheduleId, cursor, size);
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

    // 회의에 안건을 추가한다
    @Override
    @PostMapping("/api/meetings/{meetingId}/agendas")
    public ResponseEntity<BaseResponse<MeetingAgendaCreateResponse>> addAgenda(
            @PathVariable Long meetingId,
            @Valid @RequestBody MeetingAgendaCreateRequest request
    ) {
        MeetingAgendaCreateResponse response = meetingService.addAgenda(meetingId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 안건 논의 내용을 저장한다('다음 안건'/'중단하기' 공용)
    @Override
    @PatchMapping("/api/agendas/{agendaId}")
    public ResponseEntity<BaseResponse<MeetingAgendaUpdateResponse>> updateAgendaContent(
            @PathVariable Long agendaId,
            @Valid @RequestBody MeetingAgendaUpdateRequest request
    ) {
        MeetingAgendaUpdateResponse response = meetingService.updateAgendaContent(agendaId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정에 연결할 수 있는 안건 목록을 조회한다("안건 연결하기" 모달의 후보 목록)
    @Override
    @GetMapping("/api/terms/{termId}/agendas/linkable")
    public ResponseEntity<BaseResponse<MeetingLinkableAgendaListResponse>> listLinkableAgendas(
            @PathVariable Long termId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        MeetingLinkableAgendaListResponse response =
                meetingService.listLinkableAgendas(termId, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 안건을 삭제한다(안건 1개만 남으면 거부)
    @Override
    @DeleteMapping("/api/agendas/{agendaId}")
    public ResponseEntity<BaseResponse<Void>> deleteAgenda(@PathVariable Long agendaId) {
        meetingService.deleteAgenda(agendaId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 회의를 종료한다
    @Override
    @PostMapping("/api/meetings/{meetingId}/end")
    public ResponseEntity<BaseResponse<MeetingEndResponse>> endMeeting(@PathVariable Long meetingId) {
        MeetingEndResponse response = meetingService.endMeeting(meetingId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // AI로 회의 전체를 요약한다("다시 요약하기"도 동일 호출)
    @Override
    @PostMapping("/api/meetings/{meetingId}/summary")
    public ResponseEntity<BaseResponse<MeetingSummaryGenerateResponse>> generateSummary(@PathVariable Long meetingId) {
        MeetingSummaryGenerateResponse response = meetingService.generateSummary(meetingId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 사용자가 직접 고친 요약을 저장한다
    @Override
    @PatchMapping("/api/meetings/{meetingId}/summary")
    public ResponseEntity<BaseResponse<MeetingSummaryUpdateResponse>> updateSummary(
            @PathVariable Long meetingId,
            @Valid @RequestBody MeetingSummaryUpdateRequest request
    ) {
        MeetingSummaryUpdateResponse response = meetingService.updateSummary(meetingId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
