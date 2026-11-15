package com.moimzang.domain.budget.service;

// LedgerSheetWriter·LedgerExportReceiptCollector가 공유하는 행 위치 상수. 둘 중 하나만 바뀌면
// F열 링크·"장부로 돌아가기" 링크가 엉뚱한 행을 가리키게 되므로 한 곳에 모아 둔다
final class LedgerExportLayout {

    // 장부 시트의 내역 데이터가 시작되는 행(0-based). 1-based로는 7행(스펙의 "7행부터 내역")
    static final int LEDGER_DATA_START_ROW_INDEX = 6;

    private LedgerExportLayout() {
    }
}
