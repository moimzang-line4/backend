package com.moimzang.domain.settlement.controller;

import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
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
}
