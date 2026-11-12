package com.moimzang.domain.attendance.controller;

import com.moimzang.domain.attendance.dto.response.RoundAttendanceListResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.enums.AttendanceStatusFilter;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "출석", description = "차수·출석 관리 API")
public interface AttendanceApi {

    @Operation(summary = "차수 목록 조회", description = "일정의 차수 목록을 roundNo 오름차순으로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleRoundListResponse>> listRounds(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId);

    @Operation(summary = "차수 추가", description = "일정에 새 차수를 추가한다. roundNo는 마지막+1, label은 \"{roundNo}차\"로 "
            + "자동 생성하고, 그 일정의 기존 참가자 전원을 미출석 상태로 함께 포함한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ScheduleRoundCreateResponse>> createRound(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId);

    @Operation(summary = "차수 출석 현황 조회", description = "차수의 참가자 출석 현황을 cursor 페이지네이션으로 조회한다. "
            + "progress는 status/keyword 필터와 무관하게 그 차수 전체 참가자 기준으로 매번 계산한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(잘못된 cursor)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<RoundAttendanceListResponse>> getRoundAttendees(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Parameter(description = "출석 상태 필터(기본 UNCHECKED)") AttendanceStatusFilter status,
            @Parameter(description = "검색어(이름 포함검색)", example = "김모임") String keyword,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);
}
