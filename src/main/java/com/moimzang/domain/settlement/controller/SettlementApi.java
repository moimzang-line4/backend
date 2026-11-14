package com.moimzang.domain.settlement.controller;

import com.moimzang.domain.settlement.dto.request.AccountSetRequest;
import com.moimzang.domain.settlement.dto.request.PaymentConfirmRequest;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementNoticeRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.AccountResponse;
import com.moimzang.domain.settlement.dto.response.PaymentStatusResponse;
import com.moimzang.domain.settlement.dto.response.PaymentSummaryListResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeResponse;
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

    @Operation(summary = "입금 확인·되돌리기", description = "그 사람의 일정 내 모든 차수 settlement_target을 함께 "
            + "갱신한다(토글이 아니라 paid 값으로 지정). 이미 같은 상태로 재요청하면 멱등하게 처리하고 그 차수의 "
            + "paidAt을 바꾸지 않는다. 장부 반영은 하지 않는다(API-074 담당).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "처리 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404(그 일정에 정산 대상이 없는 참가자)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<PaymentStatusResponse>> updatePaymentStatus(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId,
            @Parameter(description = "참가자 ID(schedule_attendee.id)", example = "1") Long attendeeId,
            @Valid PaymentConfirmRequest request);

    @Operation(summary = "입금 명단에서 삭제", description = "일정의 모든 차수에서 그 사람의 정산 대상을 삭제하고, "
            + "각 차수는 남은 인원으로 1인 청구액을 다시 계산한다. 한 차수라도 입금 완료 상태면 423으로 거부한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404(그 일정에 정산 대상이 없는 참가자)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "423", description = "SETTLEMENT_423_PAID_CANNOT_DELETE",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> removeFromPayments(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId,
            @Parameter(description = "참가자 ID(schedule_attendee.id)", example = "1") Long attendeeId);

    @Operation(summary = "입금 현황 조회", description = "일정의 입금 현황을 cursor 페이지네이션으로 조회한다. "
            + "paymentStatus는 사람 단위 집계(그 사람의 일정 내 모든 차수가 PAID여야 PAID)이고, totalBilledAmount는 "
            + "그 사람의 모든 차수 billedAmount 합산이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(잘못된 status, 잘못된 cursor)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<PaymentSummaryListResponse>> getPaymentSummaries(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId,
            @Parameter(description = "입금 상태 필터(기본 UNPAID)", example = "UNPAID") String status,
            @Parameter(description = "검색어(이름 포함검색)", example = "김모임") String keyword,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "입금 안내 계좌 설정", description = "그 차수의 입금 안내 계좌를 등록·수정한다(upsert). "
            + "은행 연동 없이 공지에 띄울 텍스트일 뿐이라 이미 설정된 계좌가 있으면 그대로 덮어쓴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "설정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(필수 필드 누락)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<AccountResponse>> setAccount(
            @Parameter(description = "차수 ID", example = "1") Long roundId,
            @Valid AccountSetRequest request);

    @Operation(summary = "정산 공지 생성", description = "선택한 차수들의 정산 공지 문구를 만들어 응답만 반환한다(저장하지 "
            + "않음). 선택 차수의 계좌가 서로 다르면 roundNo가 가장 작은 차수의 계좌를 대표로 쓴다. 영수증은 등록됐지만 "
            + "정산 명단(API-038)을 아직 저장하지 않은 차수는 영수증 미등록과 같은 409로 거부한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "SETTLEMENT_400_ROUND_SELECTION_REQUIRED, COMMON_400(그 일정 "
                + "소속이 아니거나 존재하지 않는 차수 포함)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED, SETTLEMENT_409_ACCOUNT_NOT_SET",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<SettlementNoticeResponse>> generateSettlementNotice(
            @Parameter(description = "일정 ID", example = "1") Long scheduleId,
            @Valid SettlementNoticeRequest request);
}
