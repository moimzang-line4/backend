package com.moimzang.domain.attendance.controller;

import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.service.AttendanceService;
import com.moimzang.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
