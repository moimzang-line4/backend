package com.moimzang.domain.budget.service;

import java.util.List;

// 영수증 시트의 블록 1개 — 한 일정(schedule)에서 나온 지출 자동기록(최대 3종)이 공유하는 영수증
// 이미지 묶음이다. headerRowIndex는 영수증 시트에서(0-based), backLinkLedgerRowIndex는 장부
// 시트에서(0-based) "장부로 돌아가기" 링크가 가리킬 행이다 — 그 일정의 지출 행 중 장부 시트에
// 가장 먼저 나오는(entryDate 오름차순) 행으로 고정한다
record ReceiptBlock(int headerRowIndex, String headerLabel, List<ReceiptImageData> images,
                     int backLinkLedgerRowIndex, int rowSpan) {
}
