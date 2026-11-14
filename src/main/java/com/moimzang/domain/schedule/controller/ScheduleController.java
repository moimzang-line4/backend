package com.moimzang.domain.schedule.controller;

import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleUpdateResponse;
import com.moimzang.domain.schedule.service.ScheduleService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScheduleController implements ScheduleApi {

    private final ScheduleService scheduleService;

    // 일정을 등록한다
    @Override
    @PostMapping("/api/terms/{termId}/schedules")
    public ResponseEntity<BaseResponse<ScheduleCreateResponse>> createSchedule(
            @PathVariable Long termId,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleCreateResponse response = scheduleService.createSchedule(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정 상세를 조회한다
    @Override
    @GetMapping("/api/schedules/{scheduleId}")
    public ResponseEntity<BaseResponse<ScheduleDetailResponse>> getSchedule(@PathVariable Long scheduleId) {
        ScheduleDetailResponse response = scheduleService.getSchedule(scheduleId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정을 부분 수정한다
    @Override
    @PatchMapping("/api/schedules/{scheduleId}")
    public ResponseEntity<BaseResponse<ScheduleUpdateResponse>> updateSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        ScheduleUpdateResponse response = scheduleService.updateSchedule(scheduleId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수에 일정 카테고리를 추가한다
    @Override
    @PostMapping("/api/terms/{termId}/categories")
    public ResponseEntity<BaseResponse<ScheduleCategoryResponse>> createCategory(
            @PathVariable Long termId,
            @Valid @RequestBody ScheduleCategoryCreateRequest request
    ) {
        ScheduleCategoryResponse response = scheduleService.createCategory(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 일정 카테고리 목록을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/categories")
    public ResponseEntity<BaseResponse<ScheduleCategoryListResponse>> listCategories(@PathVariable Long termId) {
        ScheduleCategoryListResponse response = scheduleService.listCategories(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
