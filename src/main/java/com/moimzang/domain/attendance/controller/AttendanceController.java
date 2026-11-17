package com.moimzang.domain.attendance.controller;

import com.moimzang.domain.attendance.dto.request.AttendeeCheckInRequest;
import com.moimzang.domain.attendance.dto.request.MemberListImportRequest;
import com.moimzang.domain.attendance.dto.request.OnSiteAttendeeAddRequest;
import com.moimzang.domain.attendance.dto.response.MemberListImportResponse;
import com.moimzang.domain.attendance.dto.response.OnSiteAttendeeAddResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendanceListResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendeeCheckInResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.enums.AttendanceStatusFilter;
import com.moimzang.domain.attendance.service.AttendanceService;
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
public class AttendanceController implements AttendanceApi {

    private final AttendanceService attendanceService;

    // 일정의 차수 목록을 조회한다
    @Override
    @GetMapping("/api/schedules/{scheduleId}/rounds")
    public ResponseEntity<BaseResponse<ScheduleRoundListResponse>> listRounds(@PathVariable Long scheduleId) {
        ScheduleRoundListResponse response = attendanceService.listRounds(scheduleId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정에 새 차수를 추가한다(기존 참가자 전원 미출석 포함)
    @Override
    @PostMapping("/api/schedules/{scheduleId}/rounds")
    public ResponseEntity<BaseResponse<ScheduleRoundCreateResponse>> createRound(@PathVariable Long scheduleId) {
        ScheduleRoundCreateResponse response = attendanceService.createRound(scheduleId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 차수의 참가자 출석 현황을 조건(상태/검색어)에 맞게 조회한다
    @Override
    @GetMapping("/api/rounds/{roundId}/attendees")
    public ResponseEntity<BaseResponse<RoundAttendanceListResponse>> getRoundAttendees(
            @PathVariable Long roundId,
            @RequestParam(defaultValue = "UNCHECKED") AttendanceStatusFilter status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(roundId, status, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수에 현장 참가자를 미출석 상태로 추가한다
    @Override
    @PostMapping("/api/rounds/{roundId}/attendees")
    public ResponseEntity<BaseResponse<OnSiteAttendeeAddResponse>> addOnSiteAttendee(
            @PathVariable Long roundId,
            @Valid @RequestBody OnSiteAttendeeAddRequest request
    ) {
        OnSiteAttendeeAddResponse response = attendanceService.addOnSiteAttendee(roundId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 명단방의 부원 전체를 그 차수에 미출석 상태로 불러온다
    @Override
    @PostMapping("/api/rounds/{roundId}/attendees/import")
    public ResponseEntity<BaseResponse<MemberListImportResponse>> importMemberListAttendees(
            @PathVariable Long roundId,
            @Valid @RequestBody MemberListImportRequest request
    ) {
        MemberListImportResponse response = attendanceService.importMemberListAttendees(roundId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수 참가자의 출석을 처리·되돌린다
    @Override
    @PatchMapping("/api/rounds/{roundId}/attendees/{attendeeId}")
    public ResponseEntity<BaseResponse<RoundAttendeeCheckInResponse>> checkInAttendee(
            @PathVariable Long roundId,
            @PathVariable Long attendeeId,
            @Valid @RequestBody AttendeeCheckInRequest request
    ) {
        RoundAttendeeCheckInResponse response = attendanceService.checkInAttendee(roundId, attendeeId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수에서 참가자를 제외한다(이 차수의 출석 기록만 삭제)
    @Override
    @DeleteMapping("/api/rounds/{roundId}/attendees/{attendeeId}")
    public ResponseEntity<BaseResponse<Void>> removeAttendee(@PathVariable Long roundId, @PathVariable Long attendeeId) {
        attendanceService.removeAttendee(roundId, attendeeId);
        return ResponseEntity.ok(BaseResponse.success());
    }
}
