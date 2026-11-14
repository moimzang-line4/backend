package com.moimzang.domain.budget.controller;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.dto.response.LedgerExportResponse;
import com.moimzang.domain.budget.dto.response.LedgerSummaryResponse;
import com.moimzang.domain.budget.dto.response.LedgerSyncResponse;
import com.moimzang.domain.budget.service.BudgetService;
import com.moimzang.domain.budget.service.LedgerExportService;
import com.moimzang.domain.budget.service.LedgerSyncService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
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
public class BudgetController implements BudgetApi {

    private final BudgetService budgetService;
    private final LedgerSyncService ledgerSyncService;
    private final LedgerExportService ledgerExportService;

    // 예산 요약·내역을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/ledger")
    public ResponseEntity<BaseResponse<LedgerSummaryResponse>> getLedgerSummary(
            @PathVariable Long termId,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        LedgerSummaryResponse response = budgetService.getLedgerSummary(termId, type, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 장부에 직접 입력한다
    @Override
    @PostMapping("/api/terms/{termId}/ledger")
    public ResponseEntity<BaseResponse<LedgerEntryCreateResponse>> createLedgerEntry(
            @PathVariable Long termId,
            @Valid @RequestBody LedgerEntryCreateRequest request
    ) {
        LedgerEntryCreateResponse response = budgetService.createLedgerEntry(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 장부 내역 상세를 조회한다
    @Override
    @GetMapping("/api/ledger/{entryId}")
    public ResponseEntity<BaseResponse<LedgerEntryDetailResponse>> getLedgerEntry(@PathVariable Long entryId) {
        LedgerEntryDetailResponse response = budgetService.getLedgerEntry(entryId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 장부 내역을 부분 수정한다(직접 입력만 가능)
    @Override
    @PatchMapping("/api/ledger/{entryId}")
    public ResponseEntity<BaseResponse<LedgerEntryUpdateResponse>> updateLedgerEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody LedgerEntryUpdateRequest request
    ) {
        LedgerEntryUpdateResponse response = budgetService.updateLedgerEntry(entryId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 장부 내역을 삭제한다
    @Override
    @DeleteMapping("/api/ledger/{entryId}")
    public ResponseEntity<BaseResponse<Void>> deleteLedgerEntry(@PathVariable Long entryId) {
        budgetService.deleteLedgerEntry(entryId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 정산 내역을 새로고침한다(자동 기록 재계산)
    @Override
    @PostMapping("/api/terms/{termId}/ledger/sync")
    public ResponseEntity<BaseResponse<LedgerSyncResponse>> syncLedger(@PathVariable Long termId) {
        LedgerSyncResponse response = ledgerSyncService.sync(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 장부 엑셀을 생성해 다운로드 URL을 내려준다
    @Override
    @GetMapping("/api/terms/{termId}/ledger/export")
    public ResponseEntity<BaseResponse<LedgerExportResponse>> exportLedger(@PathVariable Long termId) {
        LedgerExportResponse response = ledgerExportService.export(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
