package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.dto.response.LedgerSyncResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerSyncServiceTest {

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerSyncService ledgerSyncService;

    private final GroupTerm groupTerm = groupTermWithId(1L);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ledgerSyncService = new LedgerSyncService(groupTermRepository, scheduleRepository, settlementTargetRepository, ledgerEntryRepository);
        lenient().when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private GroupTerm groupTermWithId(Long id) {
        GroupTerm term = GroupTerm.create(mock(ClubGroup.class), "26기");
        ReflectionTestUtils.setField(term, "id", id);
        return term;
    }

    private Schedule scheduleWithId(Long id, String title, LocalDateTime startAt) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", id);
        ReflectionTestUtils.setField(schedule, "title", title);
        ReflectionTestUtils.setField(schedule, "startAt", startAt);
        return schedule;
    }

    private ScheduleRound roundWithReceipt(Long id, Schedule schedule, int roundNo, int totalAmount, int clubSubsidyAmount) {
        ScheduleRound round = ScheduleRound.create(schedule, roundNo, roundNo + "차");
        round.registerReceipt("가게", LocalDateTime.of(2026, 11, 20, 19, 0), totalAmount, clubSubsidyAmount, null);
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private ScheduleRound roundWithoutReceipt(Long id, Schedule schedule, int roundNo) {
        ScheduleRound round = ScheduleRound.create(schedule, roundNo, roundNo + "차");
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private SettlementTarget target(ScheduleRound round, int billedAmount, boolean paid) {
        SettlementTarget target = SettlementTarget.create(round, ScheduleAttendee.createOnSite(round.getSchedule(), "참가자", null, null), billedAmount);
        if (paid) {
            target.updatePaymentStatus(true);
        }
        return target;
    }

    private LedgerEntry existingAuto(GroupTerm term, Schedule schedule, LedgerAutoCategory category, String itemName,
                                      Integer amount, LocalDate entryDate) {
        return LedgerEntry.createAuto(term, schedule, category, itemName, amount, entryDate);
    }

    @Test
    void sync_존재하지_않는_기수면_COMMON_404() {
        given(groupTermRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerSyncService.sync(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void sync_전원_입금_완료인_일정은_4종_자동기록을_모두_생성하고_지출_3줄_합은_영수증_총액과_같다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        SettlementTarget t1 = target(round, 20200, true);
        SettlementTarget t2 = target(round, 20200, true);
        List<SettlementTarget> targets = List.of(t1, t2);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isEqualTo(4);
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE,
                        LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE, LedgerAutoCategory.PAYMENT_INCOME);

        int receiptExpense = amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE);
        int clubSubsidyExpense = amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);
        int roundingSubsidyExpense = amountOf(saved, LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE);
        int paymentIncome = amountOf(saved, LedgerAutoCategory.PAYMENT_INCOME);

        assertThat(receiptExpense).isEqualTo(40400);
        assertThat(clubSubsidyExpense).isEqualTo(10000);
        assertThat(roundingSubsidyExpense).isEqualTo(100);
        assertThat(paymentIncome).isEqualTo(40400);
        // 지출 3줄 합 = 영수증 총액(decisions #70 불변식)
        assertThat(receiptExpense + clubSubsidyExpense + roundingSubsidyExpense).isEqualTo(50500);

        assertThat(saved).allSatisfy(entry -> {
            assertThat(entry.getEntryDate()).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(entry.getSchedule()).isEqualTo(schedule);
        });
        LedgerEntry receiptEntry = saved.stream().filter(e -> e.getAutoCategory() == LedgerAutoCategory.RECEIPT_EXPENSE).findFirst().orElseThrow();
        assertThat(receiptEntry.getItemName()).isEqualTo("가을 공연 영수증 지출");
        assertThat(receiptEntry.getType()).isEqualTo(LedgerType.EXPENSE);
        LedgerEntry incomeEntry = saved.stream().filter(e -> e.getAutoCategory() == LedgerAutoCategory.PAYMENT_INCOME).findFirst().orElseThrow();
        assertThat(incomeEntry.getItemName()).isEqualTo("가을 공연 부원 납부");
        assertThat(incomeEntry.getType()).isEqualTo(LedgerType.INCOME);
    }

    private int amountOf(List<LedgerEntry> entries, LedgerAutoCategory category) {
        return entries.stream().filter(e -> e.getAutoCategory() == category).findFirst().orElseThrow().getAmount();
    }

    @Test
    void sync_지원금이_0이고_나누어떨어지면_지원금_버림차액_줄은_생성하지_않는다() {
        Schedule schedule = scheduleWithId(10L, "번개 모임", LocalDateTime.of(2026, 11, 21, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 40000, 0);
        List<SettlementTarget> targets = List.of(target(round, 20000, false), target(round, 20000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 지원금 0, 버림 차액 0(4만원을 2명이 2만원씩 나눠 떨어짐) → 두 줄 다 생성 안 함.
        // 한 명 미입금이라 부원 납부 수입도 생성 안 함. 영수증 지출(지원금 제외 실제 청구 합계)만 생성
        assertThat(response.getCreatedCount()).isEqualTo(1);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getAutoCategory()).isEqualTo(LedgerAutoCategory.RECEIPT_EXPENSE);
        assertThat(captor.getValue().getAmount()).isEqualTo(40000);
    }

    @Test
    void sync_재호출시_금액이_같으면_updatedCount에_포함하지_않는다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, true));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);

        List<LedgerEntry> existing = List.of(
                existingAuto(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE, "가을 공연 영수증 지출", 40400, entryDate),
                existingAuto(groupTerm, schedule, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, "가을 공연 동아리 지원금", 10000, entryDate),
                existingAuto(groupTerm, schedule, LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE, "가을 공연 추가 지원금(버림 차액)", 100, entryDate),
                existingAuto(groupTerm, schedule, LedgerAutoCategory.PAYMENT_INCOME, "가을 공연 부원 납부", 40400, entryDate));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isZero();
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();
        verify(ledgerEntryRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).delete(any());
    }

    @Test
    void sync_금액이_바뀌면_updatedCount에_포함하고_값을_갱신한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, true));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);

        LedgerEntry staleReceiptEntry = existingAuto(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE,
                "가을 공연 영수증 지출", 30000, entryDate);
        List<LedgerEntry> existing = List.of(staleReceiptEntry);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getUpdatedCount()).isEqualTo(1);
        assertThat(staleReceiptEntry.getAmount()).isEqualTo(40400);
        // 나머지 3종(지원금·버림차액·수입)은 기존에 없어 새로 생성됨
        assertThat(response.getCreatedCount()).isEqualTo(3);
    }

    @Test
    void sync_조건이_깨지면_기존_자동기록을_삭제하고_직접_입력은_건드리지_않는다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        // 입금 되돌리기로 전원 완료가 깨진 상태
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, false));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);

        LedgerEntry stalePaymentIncome = existingAuto(groupTerm, schedule, LedgerAutoCategory.PAYMENT_INCOME,
                "가을 공연 부원 납부", 40400, entryDate);
        List<LedgerEntry> existing = List.of(stalePaymentIncome);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getDeletedCount()).isEqualTo(1);
        verify(ledgerEntryRepository).delete(stalePaymentIncome);
        // 직접 입력·다른 일정의 자동기록은 findByScheduleIdAndAutoCategoryIsNotNull(10L)로만 조회되므로
        // 애초에 삭제 후보에 오르지 않는다 — 다른 scheduleId로는 조회도 호출되지 않는다
        verify(ledgerEntryRepository, never()).findByScheduleIdAndAutoCategoryIsNotNull(eq(99L));
    }

    @Test
    void sync_차수_내_billedAmount가_달라도_합계로_계산한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        // 면제 등으로 billedAmount가 서로 다른 경우를 가정(현재 구현상 발생하지 않지만 방어적으로 합산해야 함)
        List<SettlementTarget> targets = List.of(target(round, 20000, true), target(round, 20500, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        ledgerSyncService.sync(1L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        int receiptExpense = amountOf(captor.getAllValues(), LedgerAutoCategory.RECEIPT_EXPENSE);
        int paymentIncome = amountOf(captor.getAllValues(), LedgerAutoCategory.PAYMENT_INCOME);

        assertThat(receiptExpense).isEqualTo(40500);
        assertThat(paymentIncome).isEqualTo(40500);
    }

    @Test
    void sync_영수증이_없는_차수의_정산대상은_전부_미반영한다() {
        Schedule schedule = scheduleWithId(10L, "영수증_미등록_일정", LocalDateTime.of(2026, 11, 22, 18, 0));
        ScheduleRound roundWithoutReceipt = roundWithoutReceipt(200L, schedule, 1);
        // 정산 대상이 있어도(비정상 상태를 방어적으로 가정) 영수증이 없는 차수는 통째로 미반영한다
        List<SettlementTarget> targets = List.of(target(roundWithoutReceipt, 10000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isZero();
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void sync_여러_차수를_모두_합산하고_지출_3줄_합은_영수증_총액_합과_같다() {
        Schedule schedule = scheduleWithId(10L, "MT", LocalDateTime.of(2026, 11, 23, 9, 0));
        ScheduleRound round1 = roundWithReceipt(100L, schedule, 1, 30300, 5000);
        ScheduleRound round2 = roundWithReceipt(101L, schedule, 2, 20100, 0);
        List<SettlementTarget> targets = List.of(
                target(round1, 12600, true), target(round1, 12600, true),
                target(round2, 10000, true), target(round2, 10000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        ledgerSyncService.sync(1L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        int receiptExpense = amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE);
        int clubSubsidyExpense = amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);
        int roundingSubsidyExpense = amountOf(saved, LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE);

        assertThat(receiptExpense).isEqualTo(45200);
        assertThat(clubSubsidyExpense).isEqualTo(5000);
        assertThat(roundingSubsidyExpense).isEqualTo(200);
        assertThat(receiptExpense + clubSubsidyExpense + roundingSubsidyExpense).isEqualTo(30300 + 20100);
    }

    @Test
    void sync_여러_일정을_모두_처리하고_개수를_합산한다() {
        Schedule schedule1 = scheduleWithId(10L, "일정1", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round1 = roundWithReceipt(100L, schedule1, 1, 20000, 0);
        List<SettlementTarget> targets1 = List.of(target(round1, 10000, true), target(round1, 10000, true));

        Schedule schedule2 = scheduleWithId(11L, "일정2", LocalDateTime.of(2026, 11, 21, 18, 0));
        ScheduleRound round2 = roundWithReceipt(101L, schedule2, 1, 20000, 0);
        List<SettlementTarget> targets2 = List.of(target(round2, 10000, true), target(round2, 10000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L, 11L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule1));
        given(scheduleRepository.findById(11L)).willReturn(Optional.of(schedule2));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets1);
        given(settlementTargetRepository.findByScheduleForAssistant(11L, null)).willReturn(targets2);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(11L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 일정마다 영수증 지출 + 부원 납부 수입(지원금 0, 버림 차액 0) = 2줄씩, 합쳐 4줄
        assertThat(response.getCreatedCount()).isEqualTo(4);
    }
}
