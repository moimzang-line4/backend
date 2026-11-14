package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerExportExcelGeneratorTest {

    private final LedgerExportExcelGenerator generator = new LedgerExportExcelGenerator();

    private GroupTerm groupTerm() {
        return GroupTerm.create(org.mockito.Mockito.mock(ClubGroup.class), "26기");
    }

    private Schedule schedule() {
        return BeanUtils.instantiateClass(Schedule.class);
    }

    private LedgerEntry directEntry(LedgerType type, String itemName, int amount, LocalDate entryDate) {
        return LedgerEntry.create(groupTerm(), type, itemName, amount, entryDate);
    }

    private LedgerEntry autoEntry(LedgerAutoCategory category, String itemName, int amount, LocalDate entryDate) {
        return LedgerEntry.createAuto(groupTerm(), schedule(), category, itemName, amount, entryDate);
    }

    @Test
    void generate_요약_3행과_이번_기수_합계_행을_채운다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "회비", 500000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.EXPENSE, "간식비", 150000, LocalDate.of(2026, 11, 5)));

        byte[] bytes = generator.generate(1_200_000, 750_000, 450_000, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("누적 수입(전체 기수)");
            assertThat(sheet.getRow(0).getCell(1).getNumericCellValue()).isEqualTo(1_200_000);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("누적 지출(전체 기수)");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(750_000);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("잔액(전체 기수)");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(450_000);

            Row termTotalsRow = sheet.getRow(3);
            assertThat(termTotalsRow.getCell(0).getStringCellValue()).isEqualTo("이번 기수 수입 합계");
            assertThat(termTotalsRow.getCell(1).getNumericCellValue()).isEqualTo(500000);
            assertThat(termTotalsRow.getCell(2).getStringCellValue()).isEqualTo("이번 기수 지출 합계");
            assertThat(termTotalsRow.getCell(3).getNumericCellValue()).isEqualTo(150000);
        }
    }

    @Test
    void generate_이번_기수_수입_지출_합계는_entries_합과_항상_일치한다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "회비1", 300000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.INCOME, "회비2", 200000, LocalDate.of(2026, 11, 2)),
                directEntry(LedgerType.EXPENSE, "지출1", 90000, LocalDate.of(2026, 11, 3)),
                directEntry(LedgerType.EXPENSE, "지출2", 60000, LocalDate.of(2026, 11, 4)));
        long expectedIncome = 300000 + 200000;
        long expectedExpense = 90000 + 60000;

        byte[] bytes = generator.generate(0, 0, 0, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row termTotalsRow = workbook.getSheetAt(0).getRow(3);
            assertThat(termTotalsRow.getCell(1).getNumericCellValue()).isEqualTo(expectedIncome);
            assertThat(termTotalsRow.getCell(3).getNumericCellValue()).isEqualTo(expectedExpense);

            // 내역 행(6행부터)의 금액 합도 같은 값이어야 한다 — 생성기가 같은 entries에서 직접 더하므로 구조적으로 일치
            Sheet sheet = workbook.getSheetAt(0);
            double rowIncomeSum = 0;
            double rowExpenseSum = 0;
            for (int i = 6; i <= 9; i++) {
                Row row = sheet.getRow(i);
                double amount = row.getCell(3).getNumericCellValue();
                if ("수입".equals(row.getCell(1).getStringCellValue())) {
                    rowIncomeSum += amount;
                } else {
                    rowExpenseSum += amount;
                }
            }
            assertThat(rowIncomeSum).isEqualTo(expectedIncome);
            assertThat(rowExpenseSum).isEqualTo(expectedExpense);
        }
    }

    @Test
    void generate_헤더와_내역_행을_entries_순서대로_채운다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "먼저", 10000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.EXPENSE, "나중", 20000, LocalDate.of(2026, 11, 2)));

        byte[] bytes = generator.generate(0, 0, 0, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(5);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("거래일자");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("구분");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("항목명");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("금액");
            assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("출처");

            Row firstDataRow = sheet.getRow(6);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("2026-11-01");
            assertThat(firstDataRow.getCell(2).getStringCellValue()).isEqualTo("먼저");
            Row secondDataRow = sheet.getRow(7);
            assertThat(secondDataRow.getCell(2).getStringCellValue()).isEqualTo("나중");
            assertThat(sheet.getRow(8)).isNull();
        }
    }

    @Test
    void generate_구분은_한글로_출처는_자동기록_여부로_표시한다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "직접 입력 수입", 10000, LocalDate.of(2026, 11, 1)),
                autoEntry(LedgerAutoCategory.RECEIPT_EXPENSE, "자동 지출", 20000, LocalDate.of(2026, 11, 2)));

        byte[] bytes = generator.generate(0, 0, 0, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row directRow = sheet.getRow(6);
            assertThat(directRow.getCell(1).getStringCellValue()).isEqualTo("수입");
            assertThat(directRow.getCell(4).getStringCellValue()).isEqualTo("직접 입력");

            Row autoRow = sheet.getRow(7);
            assertThat(autoRow.getCell(1).getStringCellValue()).isEqualTo("지출");
            assertThat(autoRow.getCell(4).getStringCellValue()).isEqualTo("정산 자동");
        }
    }

    @Test
    void generate_0건이면_요약과_헤더만_있다() throws IOException {
        byte[] bytes = generator.generate(100, 50, 50, List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getNumericCellValue()).isEqualTo(100);
            Row termTotalsRow = sheet.getRow(3);
            assertThat(termTotalsRow.getCell(1).getNumericCellValue()).isZero();
            assertThat(termTotalsRow.getCell(3).getNumericCellValue()).isZero();
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("거래일자");
            assertThat(sheet.getRow(6)).isNull();
        }
    }
}
