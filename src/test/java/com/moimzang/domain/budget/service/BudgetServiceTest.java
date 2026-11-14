package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.exception.BudgetErrorCode;
import com.moimzang.domain.budget.mapper.BudgetMapper;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
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

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final BudgetMapper budgetMapper = Mappers.getMapper(BudgetMapper.class);

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(ledgerEntryRepository, groupTermRepository, budgetMapper);
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
}
