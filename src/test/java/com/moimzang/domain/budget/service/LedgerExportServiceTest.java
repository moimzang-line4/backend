package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.budget.dto.response.LedgerExportResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerExportServiceTest {

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerExportKeyGenerator keyGenerator;

    @Mock
    private LedgerExportReceiptCollector receiptCollector;

    @Mock
    private FileStorage fileStorage;

    // 생성기는 순수 POI 로직이라 목하지 않고 실제 인스턴스를 쓴다(LedgerExportExcelGeneratorTest가 내용은 따로 검증)
    private final LedgerExportExcelGenerator excelGenerator = new LedgerExportExcelGenerator();

    private LedgerExportService ledgerExportService;

    private final ReceiptLinkingResult noReceipts = new ReceiptLinkingResult(List.of(), Map.of());

    @BeforeEach
    void setUp() {
        ledgerExportService = new LedgerExportService(
                groupTermRepository, ledgerEntryRepository, excelGenerator, keyGenerator, receiptCollector, fileStorage);
    }

    private GroupTerm groupTermWithGroup(Long id, String groupName, String termName) {
        ClubGroup group = ClubGroup.create(groupName);
        ReflectionTestUtils.setField(group, "id", 1L);
        GroupTerm term = GroupTerm.create(group, termName);
        ReflectionTestUtils.setField(term, "id", id);
        ReflectionTestUtils.setField(term, "createdAt", LocalDateTime.of(2026, 3, 1, 0, 0));
        return term;
    }

    @Test
    void export_존재하지_않는_기수면_COMMON_404() {
        given(groupTermRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerExportService.export(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void export_응답_필드를_채우고_FileStorage에_저장한다() {
        GroupTerm groupTerm = groupTermWithGroup(1L, "모임장", "26기");
        LedgerEntry entry = LedgerEntry.create(groupTerm, LedgerType.INCOME, "회비", 500000, LocalDate.of(2026, 11, 1));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(ledgerEntryRepository.findAllForExport(1L)).willReturn(List.of(entry));
        given(ledgerEntryRepository.aggregateByTypeForGroupBeforeTerm(1L, 1L)).willReturn(List.of());
        given(receiptCollector.collect(List.of(entry))).willReturn(noReceipts);
        given(keyGenerator.generate()).willReturn("ledger-exports/2026/11/fixed-key.xlsx");
        given(fileStorage.store(eq("ledger-exports/2026/11/fixed-key.xlsx"), any(InputStream.class), anyLong(), anyString()))
                .willReturn("ledger-exports/2026/11/fixed-key.xlsx");
        given(fileStorage.toUrl("ledger-exports/2026/11/fixed-key.xlsx"))
                .willReturn("http://localhost:8080/uploads/ledger-exports/2026/11/fixed-key.xlsx");

        LedgerExportResponse response = ledgerExportService.export(1L);

        assertThat(response.getDownloadUrl()).isEqualTo("http://localhost:8080/uploads/ledger-exports/2026/11/fixed-key.xlsx");
        assertThat(response.getFileName()).startsWith("모임장_26기_장부_").endsWith(".xlsx");
        assertThat(response.getGeneratedAt()).isNotNull();
    }

    @Test
    void export_저장_키는_키_생성기가_만든_값_그대로를_쓴다() {
        GroupTerm groupTerm = groupTermWithGroup(1L, "모임장", "26기");

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(ledgerEntryRepository.findAllForExport(1L)).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroupBeforeTerm(1L, 1L)).willReturn(List.of());
        given(receiptCollector.collect(List.of())).willReturn(noReceipts);
        given(keyGenerator.generate()).willReturn("ledger-exports/2026/11/abc-123.xlsx");
        given(fileStorage.toUrl(anyString())).willReturn("http://dummy");

        ledgerExportService.export(1L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(keyCaptor.capture(), any(InputStream.class), anyLong(), eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        verify(fileStorage).toUrl(keyCaptor.getValue());
        assertThat(keyCaptor.getValue()).isEqualTo("ledger-exports/2026/11/abc-123.xlsx");
    }

    @Test
    void export_초기_잔액은_이_기수보다_이전_기수의_누적값으로_계산해_A4에_리터럴로_찍는다() throws Exception {
        GroupTerm groupTerm = groupTermWithGroup(1L, "모임장", "26기");

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(ledgerEntryRepository.findAllForExport(1L)).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroupBeforeTerm(1L, 1L)).willReturn(
                List.of(new LedgerTypeTotal(LedgerType.INCOME, 900_000L, 5L),
                        new LedgerTypeTotal(LedgerType.EXPENSE, 400_000L, 4L)));
        given(receiptCollector.collect(List.of())).willReturn(noReceipts);
        given(keyGenerator.generate()).willReturn("ledger-exports/2026/11/key.xlsx");
        given(fileStorage.toUrl(anyString())).willReturn("http://dummy");

        ledgerExportService.export(1L);

        ArgumentCaptor<InputStream> contentCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(fileStorage).store(anyString(), contentCaptor.capture(), anyLong(), anyString());
        byte[] bytes = contentCaptor.getValue().readAllBytes();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row valueRow = sheet.getRow(3);
            // 900000(이전 기수 수입) - 400000(이전 기수 지출) = 500000
            assertThat(valueRow.getCell(0).getNumericCellValue()).isEqualTo(500_000);

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            assertThat(evaluator.evaluate(valueRow.getCell(5)).getNumberValue()).isEqualTo(500_000);
        }
    }

    @Test
    void export_이전_기수가_없으면_초기_잔액은_0이다() {
        GroupTerm groupTerm = groupTermWithGroup(1L, "모임장", "26기");

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(ledgerEntryRepository.findAllForExport(1L)).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroupBeforeTerm(1L, 1L)).willReturn(List.of());
        given(receiptCollector.collect(List.of())).willReturn(noReceipts);
        given(keyGenerator.generate()).willReturn("ledger-exports/2026/11/key.xlsx");
        given(fileStorage.toUrl(anyString())).willReturn("http://dummy");

        ArgumentCaptor<InputStream> contentCaptor = ArgumentCaptor.forClass(InputStream.class);

        ledgerExportService.export(1L);

        verify(fileStorage).store(anyString(), contentCaptor.capture(), anyLong(), anyString());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(contentCaptor.getValue().readAllBytes()))) {
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(0).getNumericCellValue()).isZero();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
