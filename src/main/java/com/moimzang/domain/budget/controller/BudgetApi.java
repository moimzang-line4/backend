package com.moimzang.domain.budget.controller;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.dto.response.LedgerSummaryResponse;
import com.moimzang.domain.budget.dto.response.LedgerSyncResponse;
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

@Tag(name = "예산", description = "장부·수입·지출 관리 API")
public interface BudgetApi {

    @Operation(summary = "예산 요약·내역 조회", description = "currentBalance·totalIncome·totalExpense는 그룹 전체(모든 "
            + "기수) 누적이고(기수가 바뀌어도 이월), entries는 이 기수 내역만 cursor 페이지네이션으로 조회한다(정렬: "
            + "entryDate 내림차순). 고아가 된 자동 기록도 둘 다에 그대로 포함된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(잘못된 type·cursor)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<LedgerSummaryResponse>> getLedgerSummary(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "항목 유형 필터(기본 ALL)", example = "ALL") String type,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "장부 직접 입력", description = "장부에 직접 입력한다. auto_category·schedule·scheduleRound는 "
            + "전부 null로 생성된다(자동 기록과 구분).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "입력 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<LedgerEntryCreateResponse>> createLedgerEntry(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Valid LedgerEntryCreateRequest request);

    @Operation(summary = "장부 내역 상세 조회", description = "source는 auto_category 유무로 판별한다(자동 기록이면 "
            + "RECEIPT, 직접 입력이면 MANUAL). origin은 자동 기록이면서 출처 일정이 살아있을 때만 채워지고, 직접 입력과 "
            + "출처 일정이 삭제된 고아 자동 기록은 둘 다 null이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<LedgerEntryDetailResponse>> getLedgerEntry(
            @Parameter(description = "장부 항목 ID", example = "1") Long entryId);

    @Operation(summary = "장부 내역 수정", description = "직접 입력 내역만 수정 가능하다. 보낸 필드만 반영하는 부분 수정이고, "
            + "생략한 필드는 기존 값을 유지한다. 자동 기록은 고아 여부와 무관하게 항상 거부한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "LEDGER_409_AUTO_ENTRY_LOCKED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<LedgerEntryUpdateResponse>> updateLedgerEntry(
            @Parameter(description = "장부 항목 ID", example = "1") Long entryId,
            @Valid LedgerEntryUpdateRequest request);

    @Operation(summary = "장부 내역 삭제", description = "직접 입력은 항상 삭제 가능하다. 자동 기록은 출처 일정이 살아있으면 "
            + "409로 거부하고, 일정이 삭제되어 고아가 된 뒤에는 일반 항목처럼 삭제할 수 있다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "LEDGER_409_AUTO_ENTRY_LOCKED(원본이 살아있는 경우)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteLedgerEntry(
            @Parameter(description = "장부 항목 ID", example = "1") Long entryId);

    @Operation(summary = "정산 내역 새로고침", description = "일정 단위로 최대 4종 자동 기록(영수증 지출·동아리 지원금·추가 "
            + "지원금(100원 버림 차액)·부원 납부 수입)을 계산해 반영한다. 정산 대상이 없거나 영수증이 없는 차수, 부원 납부 "
            + "수입은 그 일정 전체가 전원 입금 완료일 때만 포함한다. 금액이 0원인 줄은 생성하지 않고, 조건이 깨져 0원이 된 "
            + "기존 자동 기록은 삭제한다. 재호출해도 UNIQUE(schedule_id, auto_category) 기준으로 중복 없이 반영된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "새로고침 성공"),
        @ApiResponse(responseCode = "404", description = "COMMON_404",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<LedgerSyncResponse>> syncLedger(
            @Parameter(description = "기수 ID", example = "1") Long termId);
}
