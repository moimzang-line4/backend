package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.GroupTerm;
import java.util.List;
import java.util.Map;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

// 장부 엑셀 다운로드(API-075)의 "{기수명} 장부" 시트를 쓴다. 숫자는 초기 잔액(Java가 계산해
// 넘긴 값)만 리터럴로 찍고, 누적 수입·누적 지출·현재 잔액·행별 잔액은 전부 엑셀 수식이다 —
// 사용자가 엑셀을 열어 직접 범위를 넓혀 봐도 같은 결과가 나오게 하기 위함이다
class LedgerSheetWriter {

    private static final int[] COLUMN_WIDTHS = {16, 32, 15, 15, 22, 26};
    private static final String[] HEADERS = {"날짜", "내용", "수입", "지출", "잔액", "내역"};

    private static final int TITLE_ROW = 0;
    private static final int LABEL_ROW = 2;
    private static final int VALUE_ROW = 3;
    private static final int HEADER_ROW = 5;
    private static final int DATA_START_ROW = LedgerExportLayout.LEDGER_DATA_START_ROW_INDEX;

    void write(Sheet sheet, LedgerExportStyles styles, GroupTerm groupTerm, int year, long initialBalance,
               List<LedgerEntry> entries, Map<Integer, Integer> entryIndexToReceiptHeaderRow,
               String receiptSheetName) {
        for (int col = 0; col < COLUMN_WIDTHS.length; col++) {
            sheet.setColumnWidth(col, COLUMN_WIDTHS[col] * 256);
        }

        writeTitle(sheet, styles, groupTerm, year);
        int lastDataRow1Based = DATA_START_ROW + Math.max(entries.size(), 1);
        writeSummary(sheet, styles, initialBalance, lastDataRow1Based);
        writeHeader(sheet, styles);
        writeDataRows(sheet, styles, entries, entryIndexToReceiptHeaderRow, receiptSheetName);

        sheet.createFreezePane(0, DATA_START_ROW);
        sheet.setZoom(80);
    }

    private void writeTitle(Sheet sheet, LedgerExportStyles styles, GroupTerm groupTerm, int year) {
        Row row = sheet.createRow(TITLE_ROW);
        row.setHeightInPoints(34);
        sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW, TITLE_ROW, 0, 5));
        for (int col = 0; col < 6; col++) {
            Cell cell = row.createCell(col);
            cell.setCellStyle(styles.title());
        }
        String title = "%s 회계장부 | %s (%d)".formatted(
                groupTerm.getGroup().getName(), groupTerm.getName(), year);
        row.getCell(0).setCellValue(title);
    }

    private void writeSummary(Sheet sheet, LedgerExportStyles styles, long initialBalance, int lastDataRow1Based) {
        Row labelRow = sheet.createRow(LABEL_ROW);
        labelRow.setHeightInPoints(24);
        mergeAndStyle(sheet, labelRow, 0, 1, styles.summaryLabelNormal(), "초기 잔액");
        mergeAndStyle(sheet, labelRow, 2, 3, styles.summaryLabelNormal(), "누적 수입");
        styledCell(labelRow, 4, styles.summaryLabelNormal(), "누적 지출");
        styledCell(labelRow, 5, styles.summaryLabelHighlight(), "현재 잔액");

        Row valueRow = sheet.createRow(VALUE_ROW);
        valueRow.setHeightInPoints(35);
        mergeAndStyle(sheet, valueRow, 0, 1, styles.summaryValueNormal(), null);
        valueRow.getCell(0).setCellValue(initialBalance);
        mergeAndStyle(sheet, valueRow, 2, 3, styles.summaryValueNormal(), null);
        valueRow.getCell(2).setCellFormula("SUM(C%d:C%d)".formatted(DATA_START_ROW + 1, lastDataRow1Based));
        Cell expenseTotalCell = styledCell(valueRow, 4, styles.summaryValueNormal(), null);
        expenseTotalCell.setCellFormula("SUM(D%d:D%d)".formatted(DATA_START_ROW + 1, lastDataRow1Based));
        Cell balanceCell = styledCell(valueRow, 5, styles.summaryValueHighlight(), null);
        balanceCell.setCellFormula("A%d+C%d-E%d".formatted(VALUE_ROW + 1, VALUE_ROW + 1, VALUE_ROW + 1));
    }

    private void writeHeader(Sheet sheet, LedgerExportStyles styles) {
        Row row = sheet.createRow(HEADER_ROW);
        row.setHeightInPoints(27);
        for (int col = 0; col < HEADERS.length; col++) {
            styledCell(row, col, styles.headerRow(), HEADERS[col]);
        }
    }

    private void writeDataRows(Sheet sheet, LedgerExportStyles styles, List<LedgerEntry> entries,
                                Map<Integer, Integer> entryIndexToReceiptHeaderRow, String receiptSheetName) {
        Workbook workbook = sheet.getWorkbook();
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry entry = entries.get(i);
            int rowIndex = DATA_START_ROW + i;
            int row1Based = rowIndex + 1;
            Row row = sheet.createRow(rowIndex);
            row.setHeightInPoints(25);

            styledCell(row, 0, styles.dataDate(), entry.getEntryDate().toString());
            styledCell(row, 1, styles.dataContent(), entry.getItemName());

            Cell incomeCell = styledCell(row, 2, styles.dataIncome(), null);
            incomeCell.setCellValue(entry.getType() == LedgerType.INCOME ? entry.getAmount() : 0);
            Cell expenseCell = styledCell(row, 3, styles.dataExpense(), null);
            expenseCell.setCellValue(entry.getType() == LedgerType.EXPENSE ? entry.getAmount() : 0);

            Cell balanceCell = styledCell(row, 4, styles.dataBalance(), null);
            if (rowIndex == DATA_START_ROW) {
                balanceCell.setCellFormula("$A$%d+C%d-D%d".formatted(VALUE_ROW + 1, row1Based, row1Based));
            } else {
                balanceCell.setCellFormula("E%d+C%d-D%d".formatted(row1Based - 1, row1Based, row1Based));
            }

            Integer receiptHeaderRow = entryIndexToReceiptHeaderRow.get(i);
            Cell receiptCell = styledCell(row, 5, styles.linkCenter(), null);
            if (receiptHeaderRow != null) {
                receiptCell.setCellValue("영수증");
                Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                link.setAddress("'%s'!A%d".formatted(receiptSheetName, receiptHeaderRow + 1));
                receiptCell.setHyperlink(link);
            }
        }
    }

    private void mergeAndStyle(Sheet sheet, Row row, int firstCol, int lastCol, CellStyle style, String value) {
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), firstCol, lastCol));
        for (int col = firstCol; col <= lastCol; col++) {
            row.createCell(col).setCellStyle(style);
        }
        if (value != null) {
            row.getCell(firstCol).setCellValue(value);
        }
    }

    private Cell styledCell(Row row, int col, CellStyle style, String value) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value);
        }
        return cell;
    }
}
