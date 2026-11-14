package com.moimzang.domain.settlement.controller;

import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetImportResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetListResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetSaveResponse;
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

@Tag(name = "정산", description = "영수증·정산·입금 관리 API")
public interface SettlementApi {

    @Operation(summary = "영수증 조회", description = "그 차수의 영수증을 조회한다. isLocked는 입금 완료가 1명이라도 "
            + "있으면 true(수정·삭제 버튼 비활성)이고, registeredAt은 전용 컬럼이 없어 schedule_round.updated_at으로 대체한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ReceiptResponse>> getReceipt(
            @Parameter(description = "차수 ID", example = "1") Long roundId);

    @Operation(summary = "영수증 등록", description = "그 차수의 영수증을 등록·수정한다(upsert). 입금 완료자가 있으면 "
            + "423으로 거부하고, 금액(totalAmount/clubSubsidyAmount)이 바뀌면 그 차수에 이미 저장된 정산 명단의 1인 "
            + "청구액도 다시 계산한다. imageKey는 사진 없이 등록할 때 생략할 수 있고, 생략하면 기존 이미지를 그대로 유지한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "등록 성공"),
        @ApiResponse(responseCode = "400",
                     description = "COMMON_400 / SETTLEMENT_400_AMOUNT_NOT_POSITIVE / SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "423", description = "SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ReceiptRegisterResponse>> registerReceipt(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Valid ReceiptRegisterRequest request);

    @Operation(summary = "영수증 삭제", description = "그 차수(schedule_round) 행과 연결된 settlement_target·round_attendee를 "
            + "함께 삭제한다. 입금 완료 내역이 1건이라도 있으면 423으로 거부한다. ledger_entry는 삭제하지 않고 "
            + "schedule_round_id 참조만 NULL로 전환해 보존한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "423", description = "SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteReceipt(
            @Parameter(description = "차수 ID", example = "1") Long roundId);

    @Operation(summary = "정산 명단 조회", description = "그 차수의 정산 명단을 cursor 페이지네이션으로 조회한다. "
            + "targetCount는 페이지네이션용 totalCount가 아니라 명단 전체 인원 수이고, billedAmount는 명단 전원이 "
            + "같은 값이라 1건만 대표로 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(잘못된 cursor)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<SettlementTargetListResponse>> getSettlementTargets(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Parameter(description = "검색어(이름 포함검색)", example = "김모임") String keyword,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "정산 명단 저장", description = "보낸 attendeeIds로 그 차수의 정산 명단을 통째로 교체하고, "
            + "전원의 1인 청구액을 다시 계산한다. 영수증이 등록되지 않았으면 409, 입금 완료된 인원을 제외하려 하면 423으로 거부한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(명단 비어있음, 그 일정 소속이 아닌 참가자 포함)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "SETTLEMENT_409_RECEIPT_NOT_REGISTERED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "423", description = "SETTLEMENT_423_PAID_MEMBER_LOCKED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<SettlementTargetSaveResponse>> saveSettlementTargets(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Valid SettlementTargetSaveRequest request);

    @Operation(summary = "행사 명단 불러오기", description = "그 차수의 출석 완료자를 미리보기로 반환한다(저장 안 함). "
            + "정산 명단 저장(API-038)으로 확정해야 실제로 저장된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "SETTLEMENT_409_RECEIPT_NOT_REGISTERED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<SettlementTargetImportResponse>> importSettlementTargets(
            @Parameter(description = "차수 ID", example = "1") Long roundId);
}
