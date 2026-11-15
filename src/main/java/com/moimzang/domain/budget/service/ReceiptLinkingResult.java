package com.moimzang.domain.budget.service;

import java.util.List;
import java.util.Map;

// LedgerExportReceiptCollector의 결과. blocks는 영수증 시트에 쓸 블록 목록(헤더 행 오름차순),
// entryIndexToHeaderRow는 장부 시트의 각 내역(entries 리스트의 인덱스)이 어느 블록(영수증 시트
// 헤더 행, 0-based)에 연결되는지를 담는다 — 연결된 내역만 키로 존재한다
record ReceiptLinkingResult(List<ReceiptBlock> blocks, Map<Integer, Integer> entryIndexToHeaderRow) {
}
