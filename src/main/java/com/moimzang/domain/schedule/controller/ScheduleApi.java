package com.moimzang.domain.schedule.controller;

import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleUpdateResponse;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "일정", description = "일정·카테고리·운영 공간 관리 API")
public interface ScheduleApi {

    @Operation(summary = "일정 등록",
               description = "일정을 등록한다. createOperationSpace=true면 운영 공간과 1차 차수를 함께 만든다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "등록 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(필수 누락·색상 값 오류) 또는 SCHEDULE_400_END_BEFORE_START",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "SCHEDULE_404_GROUP_TERM_NOT_FOUND 또는 SCHEDULE_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleCreateResponse>> createSchedule(
            @Parameter(description = "기수 ID", example = "1") Long termId, @Valid ScheduleCreateRequest request);

    @Operation(summary = "일정 상세 조회", description = "일정 정보와 D-day(당일 0)를 조회한다. 수정 화면도 이 응답으로 채운다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "SCHEDULE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleDetailResponse>> getSchedule(
            @Parameter(description = "일정 ID", example = "10") Long scheduleId);

    @Operation(summary = "일정 수정", description = "보낸 필드만 수정한다. 운영 공간 토글은 수정하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400 또는 SCHEDULE_400_END_BEFORE_START",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "SCHEDULE_404_NOT_FOUND 또는 SCHEDULE_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleUpdateResponse>> updateSchedule(
            @Parameter(description = "일정 ID", example = "10") Long scheduleId, @Valid ScheduleUpdateRequest request);

    @Operation(summary = "일정 카테고리 추가", description = "일정 등록 화면의 '새 카테고리 추가'. 같은 기수에 같은 이름은 추가할 수 없다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(이름 누락·공백·길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "SCHEDULE_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "SCHEDULE_409_CATEGORY_DUPLICATED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleCategoryResponse>> createCategory(
            @Parameter(description = "기수 ID", example = "1") Long termId, @Valid ScheduleCategoryCreateRequest request);

    @Operation(summary = "일정 카테고리 목록 조회", description = "일정 등록 화면의 카테고리 선택 목록. 회의 카테고리와는 별도다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "SCHEDULE_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleCategoryListResponse>> listCategories(
            @Parameter(description = "기수 ID", example = "1") Long termId);
}
