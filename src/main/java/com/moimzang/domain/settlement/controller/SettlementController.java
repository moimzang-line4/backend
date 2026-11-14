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
import com.moimzang.domain.settlement.service.SettlementService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController implements SettlementApi {

    private final SettlementService settlementService;

    // 그 차수의 영수증을 조회한다
    @Override
    @GetMapping("/api/rounds/{roundId}/receipt")
    public ResponseEntity<BaseResponse<ReceiptResponse>> getReceipt(@PathVariable Long roundId) {
        ReceiptResponse response = settlementService.getReceipt(roundId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수의 영수증을 등록·수정한다(upsert)
    @Override
    @PutMapping("/api/rounds/{roundId}/receipt")
    public ResponseEntity<BaseResponse<ReceiptRegisterResponse>> registerReceipt(
            @PathVariable Long roundId,
            @Valid @RequestBody ReceiptRegisterRequest request
    ) {
        ReceiptRegisterResponse response = settlementService.registerReceipt(roundId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수의 영수증을 삭제한다(그 차수 자체를 cascade 삭제, 장부는 보존)
    @Override
    @DeleteMapping("/api/rounds/{roundId}/receipt")
    public ResponseEntity<BaseResponse<Void>> deleteReceipt(@PathVariable Long roundId) {
        settlementService.deleteReceipt(roundId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 그 차수의 정산 명단을 조회한다
    @Override
    @GetMapping("/api/rounds/{roundId}/settlement-targets")
    public ResponseEntity<BaseResponse<SettlementTargetListResponse>> getSettlementTargets(
            @PathVariable Long roundId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        SettlementTargetListResponse response = settlementService.getSettlementTargets(roundId, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수의 정산 명단을 통째로 교체하고 전원 재계산한다
    @Override
    @PutMapping("/api/rounds/{roundId}/settlement-targets")
    public ResponseEntity<BaseResponse<SettlementTargetSaveResponse>> saveSettlementTargets(
            @PathVariable Long roundId,
            @Valid @RequestBody SettlementTargetSaveRequest request
    ) {
        SettlementTargetSaveResponse response = settlementService.saveSettlementTargets(roundId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수의 출석 완료자를 미리보기로 반환한다(저장 안 함)
    @Override
    @GetMapping("/api/rounds/{roundId}/settlement-targets/import")
    public ResponseEntity<BaseResponse<SettlementTargetImportResponse>> importSettlementTargets(@PathVariable Long roundId) {
        SettlementTargetImportResponse response = settlementService.importSettlementTargets(roundId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 사람의 일정 내 모든 차수 입금 상태를 함께 갱신한다
    @Override
    @PatchMapping("/api/schedules/{scheduleId}/payments/{attendeeId}")
    public ResponseEntity<BaseResponse<PaymentStatusResponse>> updatePaymentStatus(
            @PathVariable Long scheduleId,
            @PathVariable Long attendeeId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentStatusResponse response = settlementService.updatePaymentStatus(scheduleId, attendeeId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 일정의 모든 차수에서 그 사람의 정산 대상을 삭제하고 남은 인원으로 재계산한다
    @Override
    @DeleteMapping("/api/schedules/{scheduleId}/payments/{attendeeId}")
    public ResponseEntity<BaseResponse<Void>> removeFromPayments(@PathVariable Long scheduleId, @PathVariable Long attendeeId) {
        settlementService.removeFromPayments(scheduleId, attendeeId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 일정의 입금 현황을 조건(상태/검색어)에 맞게 조회한다
    @Override
    @GetMapping("/api/schedules/{scheduleId}/payments")
    public ResponseEntity<BaseResponse<PaymentSummaryListResponse>> getPaymentSummaries(
            @PathVariable Long scheduleId,
            @RequestParam(defaultValue = "UNPAID") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        PaymentSummaryListResponse response = settlementService.getPaymentSummaries(scheduleId, status, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그 차수의 입금 안내 계좌를 등록·수정한다(upsert)
    @Override
    @PutMapping("/api/rounds/{roundId}/account")
    public ResponseEntity<BaseResponse<AccountResponse>> setAccount(
            @PathVariable Long roundId,
            @Valid @RequestBody AccountSetRequest request
    ) {
        AccountResponse response = settlementService.setAccount(roundId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 선택한 차수들의 정산 공지 문구를 만든다(저장하지 않고 응답만 생성)
    @Override
    @PostMapping("/api/schedules/{scheduleId}/settlement-notice")
    public ResponseEntity<BaseResponse<SettlementNoticeResponse>> generateSettlementNotice(
            @PathVariable Long scheduleId,
            @Valid @RequestBody SettlementNoticeRequest request
    ) {
        SettlementNoticeResponse response = settlementService.generateSettlementNotice(scheduleId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
