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

    @Operation(summary = "현장 인원 추가", description = "그 차수에 현장 참가자를 미출석 상태로 추가한다. 같은 일정에 이름이 "
            + "같은 참가자가 있으면 409로 거부하고, allowSameName=true로 재요청하면 새 참가자로 추가한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "ATTENDANCE_409_DUPLICATE_NAME",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<OnSiteAttendeeAddResponse>> addOnSiteAttendee(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Valid OnSiteAttendeeAddRequest request);

    @Operation(summary = "명단방 불러오기", description = "명단방의 부원 전체를 그 차수에 미출석 상태로 불러온다. 같은 "
            + "일정에 이미 그 부원으로 연결된 참가자가 있으면 재사용하고, 그 참가자가 이미 이 차수에 있으면 건너뛴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "불러오기 성공(부원이 0명이면 addedCount 0으로 성공)"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패, 그 일정과 다른 기수의 명단방)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404(존재하지 않는 차수·명단방)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberListImportResponse>> importMemberListAttendees(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Valid MemberListImportRequest request);

    @Operation(summary = "출석 처리·되돌리기", description = "그 차수 참가자의 출석 상태를 isCheckedIn 값으로 지정한다(토글이 "
            + "아니다). 이미 같은 상태로 재요청하면 멱등하게 처리하고 checkedInAt을 바꾸지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "처리 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(isCheckedIn 누락)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<RoundAttendeeCheckInResponse>> checkInAttendee(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Parameter(description = "참가자 ID(schedule_attendee.id)", example = "1") Long attendeeId,
            @Valid AttendeeCheckInRequest request);

    @Operation(summary = "참가자 제외", description = "그 차수에서 참가자를 제외한다(이 차수의 출석 기록만 삭제, "
            + "다른 차수의 출석 기록·참가자 원본은 유지).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "제외 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> removeAttendee(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Parameter(description = "참가자 ID(schedule_attendee.id)", example = "1") Long attendeeId);
}
