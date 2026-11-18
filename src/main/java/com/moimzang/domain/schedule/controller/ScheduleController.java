package com.moimzang.domain.schedule.controller;

import com.moimzang.domain.schedule.dto.request.AgendaLinkCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryUpdateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.AgendaLinkCreateResponse;
import com.moimzang.domain.schedule.dto.response.LinkedAgendaListResponse;
import com.moimzang.domain.schedule.dto.response.OperationSpaceListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleUpdateResponse;
import com.moimzang.domain.schedule.dto.response.UpcomingScheduleListResponse;
import com.moimzang.domain.schedule.enums.OperationSpaceSort;
import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceType;
import com.moimzang.domain.schedule.service.OperationSpaceService;
import com.moimzang.domain.schedule.service.ScheduleService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
public class ScheduleController implements ScheduleApi {

    private final ScheduleService scheduleService;
    private final OperationSpaceService operationSpaceService;

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

    // 캘린더 기간과 겹치는 일정을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/schedules")
    public ResponseEntity<BaseResponse<ScheduleListResponse>> listSchedules(
            @PathVariable Long termId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        ScheduleListResponse response = scheduleService.listSchedules(termId, from, to, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 다가오는 운영 일정을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/schedules/upcoming")
    public ResponseEntity<BaseResponse<UpcomingScheduleListResponse>> listUpcomingSchedules(
            @PathVariable Long termId,
            @RequestParam(required = false) Integer limit
    ) {
        UpcomingScheduleListResponse response = scheduleService.listUpcomingSchedules(termId, limit);
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

    // 일정 카테고리 이름을 수정한다
    @Override
    @PatchMapping("/api/categories/{categoryId}")
    public ResponseEntity<BaseResponse<ScheduleCategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody ScheduleCategoryUpdateRequest request
    ) {
        ScheduleCategoryResponse response = scheduleService.updateCategory(categoryId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 일정 카테고리 목록을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/categories")
    public ResponseEntity<BaseResponse<ScheduleCategoryListResponse>> listCategories(@PathVariable Long termId) {
        ScheduleCategoryListResponse response = scheduleService.listCategories(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정을 하위 데이터와 함께 삭제한다
    @Override
    @DeleteMapping("/api/schedules/{scheduleId}")
    public ResponseEntity<BaseResponse<Void>> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 운영 공간을 켠 일정과 회의록을 한 목록으로 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/operation-spaces")
    public ResponseEntity<BaseResponse<OperationSpaceListResponse>> listOperationSpaces(
            @PathVariable Long termId,
            @RequestParam OperationSpaceStatus status,
            @RequestParam(required = false) OperationSpaceType type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OperationSpaceSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        OperationSpaceListResponse response =
                operationSpaceService.listOperationSpaces(termId, status, type, keyword, sort, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 고른 안건들을 일정에 연결한다
    @Override
    @PostMapping("/api/schedules/{scheduleId}/agenda-links")
    public ResponseEntity<BaseResponse<AgendaLinkCreateResponse>> linkAgendas(
            @PathVariable Long scheduleId,
            @Valid @RequestBody AgendaLinkCreateRequest request
    ) {
        AgendaLinkCreateResponse response = operationSpaceService.linkAgendas(scheduleId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정에 연결된 안건 카드를 조회한다
    @Override
    @GetMapping("/api/schedules/{scheduleId}/agenda-links")
    public ResponseEntity<BaseResponse<LinkedAgendaListResponse>> listLinkedAgendas(@PathVariable Long scheduleId) {
        LinkedAgendaListResponse response = operationSpaceService.listLinkedAgendas(scheduleId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 안건 연결을 해제한다
    @Override
    @DeleteMapping("/api/schedules/{scheduleId}/agenda-links/{linkId}")
    public ResponseEntity<BaseResponse<Void>> unlinkAgenda(@PathVariable Long scheduleId, @PathVariable Long linkId) {
        operationSpaceService.unlinkAgenda(scheduleId, linkId);
        return ResponseEntity.ok(BaseResponse.success());
    }
}
