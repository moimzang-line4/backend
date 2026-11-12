package com.moimzang.domain.attendance.controller;

import com.moimzang.domain.attendance.dto.response.RoundAttendanceListResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.enums.AttendanceStatusFilter;
import com.moimzang.domain.attendance.service.AttendanceService;
import com.moimzang.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
