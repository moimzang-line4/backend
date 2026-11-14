package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

// 장부 엑셀 다운로드(API-075)의 xlsx 바이트를 만든다. 요약 값(누적 수입·지출·잔액)은 호출하는
// 쪽이 aggregateByTypeForGroup(API-069와 같은 집계)로 구한 값을 그대로 받고, 이번 기수
// 수입·지출 합계는 실제로 찍는 entries에서 직접 더해 "내역 행 합계와 항상 일치"를 구조적으로
// 보장한다(별도 쿼리로 다시 계산하면 둘이 어긋날 여지가 생긴다)
@Component
public class LedgerExportExcelGenerator {

    private static final String[] HEADERS = {"거래일자", "구분", "항목명", "금액", "출처"};
    private static final String AMOUNT_FORMAT = "#,##0";

    public byte[] generate(long groupTotalIncome, long groupTotalExpense, long groupCurrentBalance,
                            List<LedgerEntry> entries) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("장부");
            CellStyle amountStyle = createAmountStyle(workbook);

            int rowIndex = 0;
            rowIndex = writeSummaryRow(sheet, rowIndex, "누적 수입(전체 기수)", groupTotalIncome, amountStyle);
            rowIndex = writeSummaryRow(sheet, rowIndex, "누적 지출(전체 기수)", groupTotalExpense, amountStyle);
            rowIndex = writeSummaryRow(sheet, rowIndex, "잔액(전체 기수)", groupCurrentBalance, amountStyle);
            rowIndex = writeTermTotalsRow(sheet, rowIndex, entries, amountStyle);
            rowIndex++; // 빈 행
            rowIndex = writeHeaderRow(sheet, rowIndex);
            writeEntryRows(sheet, rowIndex, entries, amountStyle);

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("장부 엑셀 생성 중 입출력 오류가 발생했다", e);
        }
    }

    private CellStyle createAmountStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat(AMOUNT_FORMAT));
        return style;
    }

    private int writeSummaryRow(Sheet sheet, int rowIndex, String label, long amount, CellStyle amountStyle) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        writeAmountCell(row, 1, amount, amountStyle);
        return rowIndex + 1;
    }

    // 이번 기수 수입·지출 합계를 한 행에 같이 적는다. entries를 직접 더해서 바로 아래 내역
    // 행들의 합계와 항상 일치한다
    private int writeTermTotalsRow(Sheet sheet, int rowIndex, List<LedgerEntry> entries, CellStyle amountStyle) {
        long termIncome = entries.stream()
                .filter(entry -> entry.getType() == LedgerType.INCOME)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        long termExpense = entries.stream()
                .filter(entry -> entry.getType() == LedgerType.EXPENSE)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue("이번 기수 수입 합계");
        writeAmountCell(row, 1, termIncome, amountStyle);
        row.createCell(2).setCellValue("이번 기수 지출 합계");
        writeAmountCell(row, 3, termExpense, amountStyle);
        return rowIndex + 1;
    }

    private int writeHeaderRow(Sheet sheet, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < HEADERS.length; i++) {
            row.createCell(i).setCellValue(HEADERS[i]);
        }
        return rowIndex + 1;
    }

    private void writeEntryRows(Sheet sheet, int startRowIndex, List<LedgerEntry> entries, CellStyle amountStyle) {
        int rowIndex = startRowIndex;
        for (LedgerEntry entry : entries) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getEntryDate().toString());
            row.createCell(1).setCellValue(typeLabel(entry.getType()));
            row.createCell(2).setCellValue(entry.getItemName());
            writeAmountCell(row, 3, entry.getAmount(), amountStyle);
            row.createCell(4).setCellValue(sourceLabel(entry));
        }
    }

    private void writeAmountCell(Row row, int columnIndex, long amount, CellStyle amountStyle) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(amount);
        cell.setCellStyle(amountStyle);
    }

    private String typeLabel(LedgerType type) {
        return type == LedgerType.INCOME ? "수입" : "지출";
    }

    // 자동 기록 여부로 출처를 판별한다(decisions #65와 같은 기준)
    private String sourceLabel(LedgerEntry entry) {
        return entry.isAutoEntry() ? "정산 자동" : "직접 입력";
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
