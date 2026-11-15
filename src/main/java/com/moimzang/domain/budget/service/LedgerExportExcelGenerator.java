package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.group.entity.GroupTerm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

// 장부 엑셀 다운로드(API-075)의 xlsx 바이트를 만든다. 실제 셀 작성은 시트별로 역할을 나눈
// LedgerSheetWriter·ReceiptSheetWriter가 담당하고, 이 클래스는 workbook 생성과 공통 스타일
// (LedgerExportStyles, workbook당 한 번만 생성) 준비, 두 시트 오케스트레이션만 한다.
// 영수증 이미지 로딩·일정-내역 연결 계산(DB·FileStorage 접근)은 순수 POI 로직과 분리해
// LedgerExportReceiptCollector가 미리 끝내 두고, 여기서는 그 결과만 받는다
@Component
public class LedgerExportExcelGenerator {

    private static final String RECEIPT_SHEET_NAME = "영수증";
    // Excel 시트명은 31자 제한, 콜론 등 일부 특수문자를 허용하지 않는다 — 기수명은 자유 텍스트라
    // 길거나 그런 문자를 포함할 수 있어 방어적으로 다듬는다
    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String INVALID_SHEET_NAME_CHARS = "[:\\\\/?*\\[\\]]";

    public byte[] generate(GroupTerm groupTerm, long initialBalance, List<LedgerEntry> entries,
                            ReceiptLinkingResult receiptLinking) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.setForceFormulaRecalculation(true);
            LedgerExportStyles styles = new LedgerExportStyles(workbook);

            String ledgerSheetName = sanitizeSheetName(groupTerm.getName() + " 장부");
            Sheet ledgerSheet = workbook.createSheet(ledgerSheetName);
            Sheet receiptSheet = workbook.createSheet(RECEIPT_SHEET_NAME);

            int year = groupTerm.getCreatedAt().getYear();
            new LedgerSheetWriter().write(ledgerSheet, styles, groupTerm, year, initialBalance, entries,
                    receiptLinking.entryIndexToHeaderRow(), RECEIPT_SHEET_NAME);
            new ReceiptSheetWriter().write(receiptSheet, styles, receiptLinking.blocks(), ledgerSheetName);

            evaluateFormulas(workbook);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("장부 엑셀 생성 중 입출력 오류가 발생했다", e);
        }
    }

    // Excel이 열 때 재계산하도록 설정해 뒀지만(setForceFormulaRecalculation), POI로 바로 결과를
    // 읽는 다른 도구(테스트 등)를 위해 캐시된 값도 여기서 직접 채워 둔다
    private void evaluateFormulas(Workbook workbook) {
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
    }

    private String sanitizeSheetName(String raw) {
        String cleaned = raw.replaceAll(INVALID_SHEET_NAME_CHARS, "");
        return cleaned.length() > MAX_SHEET_NAME_LENGTH ? cleaned.substring(0, MAX_SHEET_NAME_LENGTH) : cleaned;
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
