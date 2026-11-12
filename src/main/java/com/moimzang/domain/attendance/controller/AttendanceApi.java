package com.moimzang.domain.attendance.controller;

import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
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
}
