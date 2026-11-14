package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.dto.response.LedgerSummaryResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.exception.BudgetErrorCode;
import com.moimzang.domain.budget.mapper.BudgetMapper;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private FileRepository fileRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final BudgetMapper budgetMapper = Mappers.getMapper(BudgetMapper.class);

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(ledgerEntryRepository, groupTermRepository, fileRepository, budgetMapper);
    }

    private GroupTerm groupTermWithId(Long id) {
        GroupTerm groupTerm = GroupTerm.create(mock(ClubGroup.class), "26기");
        ReflectionTestUtils.setField(groupTerm, "id", id);
        return groupTerm;
    }

    private Schedule scheduleWithId(Long id, String title) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", id);
        ReflectionTestUtils.setField(schedule, "title", title);
        return schedule;
    }

    private LedgerEntry directEntryWithId(Long id, LedgerType type, String itemName, Integer amount, LocalDate entryDate) {
        LedgerEntry entry = LedgerEntry.create(groupTermWithId(1L), type, itemName, amount, entryDate);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private LedgerEntry autoEntryWithId(Long id, LedgerAutoCategory autoCategory, Schedule schedule) {
        LedgerEntry entry = BeanUtils.instantiateClass(LedgerEntry.class);
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "type", LedgerType.EXPENSE);
        ReflectionTestUtils.setField(entry, "autoCategory", autoCategory);
        ReflectionTestUtils.setField(entry, "schedule", schedule);
        ReflectionTestUtils.setField(entry, "itemName", "영수증 지출");
        ReflectionTestUtils.setField(entry, "amount", 30000);
        ReflectionTestUtils.setField(entry, "entryDate", LocalDate.of(2026, 11, 1));
        return entry;
    }

    private File fileWithId(Long id, String name) {
        File file = BeanUtils.instantiateClass(File.class);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "name", name);
        return file;
    }

    @Test
    void createLedgerEntry_직접_입력을_저장하고_entryId를_반환한다() {
        LedgerEntryCreateRequest request = LedgerEntryCreateRequest.builder()
                .type(LedgerType.EXPENSE)
                .itemName("간식비")
                .amount(15000)
                .entryDate(LocalDate.of(2026, 11, 15))
                .build();
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.save(any(LedgerEntry.class))).willAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        LedgerEntryCreateResponse response = budgetService.createLedgerEntry(1L, request);

        assertThat(response.getEntryId()).isEqualTo(100L);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry savedEntry = captor.getValue();
        assertThat(savedEntry.getType()).isEqualTo(LedgerType.EXPENSE);
        assertThat(savedEntry.getItemName()).isEqualTo("간식비");
        assertThat(savedEntry.getAmount()).isEqualTo(15000);
        assertThat(savedEntry.getEntryDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(savedEntry.isAutoEntry()).isFalse();
        assertThat(savedEntry.getSchedule()).isNull();
    }

    @Test
    void createLedgerEntry_존재하지_않는_기수면_COMMON_404() {
        LedgerEntryCreateRequest request = LedgerEntryCreateRequest.builder()
                .type(LedgerType.EXPENSE)
                .itemName("간식비")
                .amount(15000)
                .entryDate(LocalDate.of(2026, 11, 15))
                .build();
        given(groupTermRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.createLedgerEntry(999L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void getLedgerEntry_직접_입력이면_source는_MANUAL이고_origin은_null이다() {
        LedgerEntry entry = directEntryWithId(1L, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 15));
        given(ledgerEntryRepository.findById(1L)).willReturn(Optional.of(entry));

        LedgerEntryDetailResponse response = budgetService.getLedgerEntry(1L);

        assertThat(response.getEntryId()).isEqualTo(1L);
        assertThat(response.getSource()).isEqualTo(LedgerSource.MANUAL);
        assertThat(response.getOrigin()).isNull();
    }

    @Test
    void getLedgerEntry_자동_기록이고_일정이_살아있으면_source는_RECEIPT이고_origin을_채운다() {
        Schedule schedule = scheduleWithId(5L, "정기 모임");
        LedgerEntry entry = autoEntryWithId(2L, LedgerAutoCategory.RECEIPT_EXPENSE, schedule);
        given(ledgerEntryRepository.findById(2L)).willReturn(Optional.of(entry));

        LedgerEntryDetailResponse response = budgetService.getLedgerEntry(2L);

        assertThat(response.getSource()).isEqualTo(LedgerSource.RECEIPT);
        assertThat(response.getOrigin()).isNotNull();
        assertThat(response.getOrigin().getScheduleId()).isEqualTo(5L);
        assertThat(response.getOrigin().getTitle()).isEqualTo("정기 모임");
    }

    @Test
    void getLedgerEntry_자동_기록이지만_고아이면_source는_RECEIPT이고_origin은_null이다() {
        LedgerEntry entry = autoEntryWithId(3L, LedgerAutoCategory.RECEIPT_EXPENSE, null);
        given(ledgerEntryRepository.findById(3L)).willReturn(Optional.of(entry));

        LedgerEntryDetailResponse response = budgetService.getLedgerEntry(3L);

        assertThat(response.getSource()).isEqualTo(LedgerSource.RECEIPT);
        assertThat(response.getOrigin()).isNull();
    }

    @Test
    void getLedgerEntry_존재하지_않으면_COMMON_404() {
        given(ledgerEntryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getLedgerEntry(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void updateLedgerEntry_직접_입력은_보낸_필드만_반영한다() {
        LedgerEntry entry = directEntryWithId(1L, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 15));
        given(ledgerEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder()
                .amount(20000)
                .build();

        LedgerEntryUpdateResponse response = budgetService.updateLedgerEntry(1L, request);

        assertThat(response.getAmount()).isEqualTo(20000);
        assertThat(response.getItemName()).isEqualTo("간식비");
        assertThat(response.getType()).isEqualTo(LedgerType.EXPENSE);
        assertThat(response.getEntryDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void updateLedgerEntry_자동_기록이면_원본이_살아있어도_LEDGER_409_AUTO_ENTRY_LOCKED() {
        Schedule schedule = scheduleWithId(5L, "정기 모임");
        LedgerEntry entry = autoEntryWithId(2L, LedgerAutoCategory.RECEIPT_EXPENSE, schedule);
        given(ledgerEntryRepository.findById(2L)).willReturn(Optional.of(entry));
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(1000).build();

        assertThatThrownBy(() -> budgetService.updateLedgerEntry(2L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(BudgetErrorCode.LEDGER_409_AUTO_ENTRY_LOCKED);
    }

    @Test
    void updateLedgerEntry_자동_기록이_고아여도_LEDGER_409_AUTO_ENTRY_LOCKED() {
        LedgerEntry entry = autoEntryWithId(3L, LedgerAutoCategory.RECEIPT_EXPENSE, null);
        given(ledgerEntryRepository.findById(3L)).willReturn(Optional.of(entry));
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(1000).build();

        assertThatThrownBy(() -> budgetService.updateLedgerEntry(3L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(BudgetErrorCode.LEDGER_409_AUTO_ENTRY_LOCKED);
    }

    @Test
    void updateLedgerEntry_존재하지_않으면_COMMON_404() {
        given(ledgerEntryRepository.findById(999L)).willReturn(Optional.empty());
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(1000).build();

        assertThatThrownBy(() -> budgetService.updateLedgerEntry(999L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void deleteLedgerEntry_직접_입력은_항상_삭제된다() {
        LedgerEntry entry = directEntryWithId(1L, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 15));
        given(ledgerEntryRepository.findById(1L)).willReturn(Optional.of(entry));

        budgetService.deleteLedgerEntry(1L);

        verify(ledgerEntryRepository).delete(entry);
    }

    @Test
    void deleteLedgerEntry_자동_기록이고_원본_일정이_살아있으면_LEDGER_409_AUTO_ENTRY_LOCKED() {
        Schedule schedule = scheduleWithId(5L, "정기 모임");
        LedgerEntry entry = autoEntryWithId(2L, LedgerAutoCategory.RECEIPT_EXPENSE, schedule);
        given(ledgerEntryRepository.findById(2L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> budgetService.deleteLedgerEntry(2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(BudgetErrorCode.LEDGER_409_AUTO_ENTRY_LOCKED);
        verify(ledgerEntryRepository, never()).delete(any());
    }

    @Test
    void deleteLedgerEntry_자동_기록이_고아이면_삭제된다() {
        LedgerEntry entry = autoEntryWithId(3L, LedgerAutoCategory.RECEIPT_EXPENSE, null);
        given(ledgerEntryRepository.findById(3L)).willReturn(Optional.of(entry));

        budgetService.deleteLedgerEntry(3L);

        verify(ledgerEntryRepository).delete(entry);
    }

    @Test
    void deleteLedgerEntry_존재하지_않으면_COMMON_404() {
        given(ledgerEntryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.deleteLedgerEntry(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getLedgerSummary_존재하지_않는_기수면_COMMON_404() {
        given(groupTermRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getLedgerSummary(999L, "ALL", null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getLedgerSummary_잘못된_cursor이면_COMMON_400() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));

        assertThatThrownBy(() -> budgetService.getLedgerSummary(1L, "ALL", "깨진cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getLedgerSummary_잘못된_type이면_COMMON_400() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));

        assertThatThrownBy(() -> budgetService.getLedgerSummary(1L, "INVALID", null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getLedgerSummary_currentBalance는_그룹_전체_수입에서_지출을_뺀_값이다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of(
                new LedgerTypeTotal(LedgerType.INCOME, 1000000L, 3L),
                new LedgerTypeTotal(LedgerType.EXPENSE, 400000L, 2L)));
        given(fileRepository.findActiveFile(any(), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getTotalIncome()).isEqualTo(1000000L);
        assertThat(response.getTotalExpense()).isEqualTo(400000L);
        assertThat(response.getCurrentBalance()).isEqualTo(600000L);
    }

    @Test
    void getLedgerSummary_수입이나_지출이_한쪽도_없으면_0으로_채운다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroup(any()))
                .willReturn(List.of(new LedgerTypeTotal(LedgerType.INCOME, 500000L, 1L)));
        given(fileRepository.findActiveFile(any(), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getTotalIncome()).isEqualTo(500000L);
        assertThat(response.getTotalExpense()).isEqualTo(0L);
        assertThat(response.getCurrentBalance()).isEqualTo(500000L);
    }

    @Test
    void getLedgerSummary_장부_파일이_있으면_ledgerFile을_채운다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of());
        given(fileRepository.findActiveFile(eq(1L), any())).willReturn(Optional.of(fileWithId(7L, "장부.xlsx")));

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getLedgerFile()).isNotNull();
        assertThat(response.getLedgerFile().getFileId()).isEqualTo(7L);
        assertThat(response.getLedgerFile().getName()).isEqualTo("장부.xlsx");
    }

    @Test
    void getLedgerSummary_장부_파일이_없으면_ledgerFile은_null이다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of());
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of());
        given(fileRepository.findActiveFile(eq(1L), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getLedgerFile()).isNull();
    }

    @Test
    void getLedgerSummary_entries의_source를_직접입력_자동기록으로_각각_매핑한다() {
        LedgerEntry manual = directEntryWithId(1L, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 10));
        LedgerEntry auto = autoEntryWithId(2L, LedgerAutoCategory.RECEIPT_EXPENSE, scheduleWithId(5L, "정기 모임"));
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of(manual, auto));
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of());
        given(fileRepository.findActiveFile(any(), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getEntries()).extracting("entryId", "source")
                .containsExactly(
                        tuple(1L, LedgerSource.MANUAL),
                        tuple(2L, LedgerSource.RECEIPT));
    }

    @Test
    void getLedgerSummary_다음_페이지가_있으면_nextCursor를_만든다() {
        LedgerEntry first = directEntryWithId(1L, LedgerType.EXPENSE, "첫번째", 10000, LocalDate.of(2026, 11, 10));
        LedgerEntry second = directEntryWithId(2L, LedgerType.EXPENSE, "두번째", 20000, LocalDate.of(2026, 11, 5));
        LedgerEntry extra = directEntryWithId(3L, LedgerType.EXPENSE, "다음페이지용", 30000, LocalDate.of(2026, 11, 1));
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), eq(3)))
                .willReturn(List.of(first, second, extra));
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of());
        given(fileRepository.findActiveFile(any(), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, 2);

        assertThat(response.getEntries()).hasSize(2);
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void getLedgerSummary_마지막_페이지면_nextCursor는_null이다() {
        LedgerEntry only = directEntryWithId(1L, LedgerType.EXPENSE, "유일한 항목", 10000, LocalDate.of(2026, 11, 10));
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTermWithId(1L)));
        given(ledgerEntryRepository.search(eq(1L), any(), any(), any(), anyInt())).willReturn(List.of(only));
        given(ledgerEntryRepository.aggregateByTypeForGroup(any())).willReturn(List.of());
        given(fileRepository.findActiveFile(any(), any())).willReturn(Optional.empty());

        LedgerSummaryResponse response = budgetService.getLedgerSummary(1L, "ALL", null, null);

        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getNextCursor()).isNull();
    }
}
