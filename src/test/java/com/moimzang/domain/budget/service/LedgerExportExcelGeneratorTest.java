package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerExportExcelGeneratorTest {

    private final LedgerExportExcelGenerator generator = new LedgerExportExcelGenerator();

    private GroupTerm groupTerm(String groupName, String termName, int createdYear) {
        ClubGroup group = ClubGroup.create(groupName);
        GroupTerm term = GroupTerm.create(group, termName);
        ReflectionTestUtils.setField(term, "createdAt", LocalDateTime.of(createdYear, 3, 1, 0, 0));
        return term;
    }

    private LedgerEntry directEntry(LedgerType type, String itemName, int amount, LocalDate entryDate) {
        return LedgerEntry.create(groupTerm("모임장", "26기", 2026), type, itemName, amount, entryDate);
    }

    private ReceiptLinkingResult noReceipts() {
        return new ReceiptLinkingResult(List.of(), Map.of());
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void generate_시트_두_개를_만들고_이름을_붙인다() {
        GroupTerm groupTerm = groupTerm("모임장", "26기", 2026);

        byte[] bytes = generator.generate(groupTerm, 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("26기 장부");
            assertThat(workbook.getSheetName(1)).isEqualTo("영수증");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_제목을_채우고_병합한다() {
        GroupTerm groupTerm = groupTerm("모임장", "26기", 2026);

        byte[] bytes = generator.generate(groupTerm, 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("모임장 회계장부 | 26기 (2026)");
            assertThat(sheet.getMergedRegions()).contains(new CellRangeAddress(0, 0, 0, 5));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_요약_라벨을_채우고_병합한다() {
        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row labelRow = sheet.getRow(2);
            assertThat(labelRow.getCell(0).getStringCellValue()).isEqualTo("초기 잔액");
            assertThat(labelRow.getCell(2).getStringCellValue()).isEqualTo("누적 수입");
            assertThat(labelRow.getCell(4).getStringCellValue()).isEqualTo("누적 지출");
            assertThat(labelRow.getCell(5).getStringCellValue()).isEqualTo("현재 잔액");
            assertThat(sheet.getMergedRegions()).contains(
                    new CellRangeAddress(2, 2, 0, 1), new CellRangeAddress(2, 2, 2, 3));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_요약_값은_초기잔액만_리터럴이고_나머지는_수식이다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "회비", 500000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.EXPENSE, "간식비", 150000, LocalDate.of(2026, 11, 5)));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 300000, entries, noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row valueRow = sheet.getRow(3);

            assertThat(valueRow.getCell(0).getCellType().toString()).isEqualTo("NUMERIC");
            assertThat(valueRow.getCell(0).getNumericCellValue()).isEqualTo(300000);

            assertThat(evaluator.evaluate(valueRow.getCell(2)).getNumberValue()).isEqualTo(500000);
            assertThat(evaluator.evaluate(valueRow.getCell(4)).getNumberValue()).isEqualTo(150000);
            assertThat(evaluator.evaluate(valueRow.getCell(5)).getNumberValue()).isEqualTo(300000 + 500000 - 150000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_헤더와_데이터행을_채운다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "먼저", 10000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.EXPENSE, "나중", 20000, LocalDate.of(2026, 11, 2)));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(5);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("날짜");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("내용");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("수입");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("지출");
            assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("잔액");
            assertThat(headerRow.getCell(5).getStringCellValue()).isEqualTo("내역");

            Row firstDataRow = sheet.getRow(6);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("2026-11-01");
            assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("먼저");
            assertThat(firstDataRow.getCell(2).getNumericCellValue()).isEqualTo(10000);
            assertThat(firstDataRow.getCell(3).getNumericCellValue()).isZero();

            Row secondDataRow = sheet.getRow(7);
            assertThat(secondDataRow.getCell(1).getStringCellValue()).isEqualTo("나중");
            assertThat(secondDataRow.getCell(2).getNumericCellValue()).isZero();
            assertThat(secondDataRow.getCell(3).getNumericCellValue()).isEqualTo(20000);

            assertThat(sheet.getRow(8)).isNull();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_행별_잔액_수식을_체인으로_평가한다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.INCOME, "1", 10000, LocalDate.of(2026, 11, 1)),
                directEntry(LedgerType.EXPENSE, "2", 3000, LocalDate.of(2026, 11, 2)),
                directEntry(LedgerType.INCOME, "3", 5000, LocalDate.of(2026, 11, 3)));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 1000, entries, noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // 초기잔액 1000 + 10000 - 0 = 11000
            assertThat(evaluator.evaluate(sheet.getRow(6).getCell(4)).getNumberValue()).isEqualTo(11000);
            // 11000 + 0 - 3000 = 8000
            assertThat(evaluator.evaluate(sheet.getRow(7).getCell(4)).getNumberValue()).isEqualTo(8000);
            // 8000 + 5000 - 0 = 13000
            assertThat(evaluator.evaluate(sheet.getRow(8).getCell(4)).getNumberValue()).isEqualTo(13000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_내역이_0건이어도_요약_수식이_에러_없이_0으로_평가된다() {
        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 50000, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row valueRow = sheet.getRow(3);

            assertThat(evaluator.evaluate(valueRow.getCell(2)).getNumberValue()).isZero();
            assertThat(evaluator.evaluate(valueRow.getCell(4)).getNumberValue()).isZero();
            assertThat(evaluator.evaluate(valueRow.getCell(5)).getNumberValue()).isEqualTo(50000);
            assertThat(sheet.getRow(6)).isNull();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_틀고정을_6행까지_설정한다() {
        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            PaneInformation paneInformation = workbook.getSheetAt(0).getPaneInformation();
            assertThat(paneInformation.isFreezePane()).isTrue();
            assertThat(paneInformation.getHorizontalSplitPosition()).isEqualTo((short) 6);
            assertThat(paneInformation.getVerticalSplitPosition()).isEqualTo((short) 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_F열에_영수증_링크가_있으면_영수증_시트_주소로_하이퍼링크를_만든다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.EXPENSE, "영수증 있는 지출", 10000, LocalDate.of(2026, 11, 1)));
        ReceiptBlock block = new ReceiptBlock(2, "영수증 있는 지출", List.of(), 6, 26);
        ReceiptLinkingResult linking = new ReceiptLinkingResult(List.of(block), Map.of(0, 2));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, linking);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell receiptCell = workbook.getSheetAt(0).getRow(6).getCell(5);
            assertThat(receiptCell.getStringCellValue()).isEqualTo("영수증");
            Hyperlink link = receiptCell.getHyperlink();
            assertThat(link).isNotNull();
            assertThat(link.getAddress()).isEqualTo("'영수증'!A3");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_F열에_연결된_영수증이_없으면_빈칸이다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.EXPENSE, "직접 입력", 10000, LocalDate.of(2026, 11, 1)));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell receiptCell = workbook.getSheetAt(0).getRow(6).getCell(5);
            assertThat(receiptCell.getHyperlink()).isNull();
            assertThat(receiptCell.getStringCellValue()).isEmpty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_영수증_시트_블록_헤더와_돌아가기_링크를_만든다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.EXPENSE, "영수증 있는 지출", 10000, LocalDate.of(2026, 11, 1)));
        ReceiptBlock block = new ReceiptBlock(2, "영수증 있는 지출",
                List.of(new ReceiptImageData(pngBytes(100, 100), org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG, 100, 100)),
                6, 26);
        ReceiptLinkingResult linking = new ReceiptLinkingResult(List.of(block), Map.of(0, 2));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, linking);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet receiptSheet = workbook.getSheetAt(1);
            Row headerRow = receiptSheet.getRow(2);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("영수증 있는 지출");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("장부로 돌아가기");
            Hyperlink backLink = headerRow.getCell(1).getHyperlink();
            assertThat(backLink.getAddress()).isEqualTo("'26기 장부'!F7");
        }
    }

    @Test
    void generate_이미지를_정상적으로_삽입한다() throws IOException {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.EXPENSE, "영수증 있는 지출", 10000, LocalDate.of(2026, 11, 1)));
        ReceiptBlock block = new ReceiptBlock(2, "영수증 있는 지출",
                List.of(new ReceiptImageData(pngBytes(200, 300), org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG, 200, 300)),
                6, 26);
        ReceiptLinkingResult linking = new ReceiptLinkingResult(List.of(block), Map.of(0, 2));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, linking);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getAllPictures()).hasSize(1);
        }
    }

    @Test
    void generate_이미지_로딩_실패시_안내_문구를_넣는다() {
        List<LedgerEntry> entries = List.of(
                directEntry(LedgerType.EXPENSE, "영수증 있는 지출", 10000, LocalDate.of(2026, 11, 1)));
        ReceiptBlock block = new ReceiptBlock(2, "영수증 있는 지출",
                List.of(ReceiptImageData.failed()), 6, 26);
        ReceiptLinkingResult linking = new ReceiptLinkingResult(List.of(block), Map.of(0, 2));

        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, entries, linking);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet receiptSheet = workbook.getSheetAt(1);
            Row imageRow = receiptSheet.getRow(3);
            assertThat(imageRow.getCell(0).getStringCellValue()).isEqualTo("이미지를 불러올 수 없습니다");
            assertThat(workbook.getAllPictures()).isEmpty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_영수증이_0건이면_영수증_시트는_제목만_있다() {
        byte[] bytes = generator.generate(groupTerm("모임장", "26기", 2026), 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet receiptSheet = workbook.getSheetAt(1);
            assertThat(receiptSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("영수증 모음");
            assertThat(receiptSheet.getRow(2)).isNull();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generate_기수명이_길면_시트명을_31자로_자른다() {
        String longTermName = "아주아주아주아주아주아주아주아주아주아주긴기수명";
        GroupTerm groupTerm = groupTerm("모임장", longTermName, 2026);

        byte[] bytes = generator.generate(groupTerm, 0, List.of(), noReceipts());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0)).hasSizeLessThanOrEqualTo(31);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
