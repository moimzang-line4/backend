package com.moimzang.domain.settlement.controller;

import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
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
}
