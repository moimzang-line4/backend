package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

// 직접 입력·고아(schedule_id null)·다른 기수 자동기록이 sync로 건드려지지 않는다는 보장은 이
// 클래스가 아니라 LedgerEntryRepositoryImplTest의 findAutoScheduleIds_직접_입력만_있으면_제외한다·
// findAutoScheduleIds_schedule이_null인_고아_자동기록은_제외한다·findAutoScheduleIds_다른_기수의_자동기록은_제외한다·
// findByScheduleIdAndAutoCategoryIsNotNull_직접_입력은_제외하고_그_일정의_자동기록만_반환한다 네 건이 맡는다 —
// 이 서비스는 그 쿼리들이 돌려준 결과만 보고 동작하므로, 거기서 제외된 레코드는 여기서도 결코 대상이 될 수 없다
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
    void sync_조건이_깨지면_기존_자동기록을_삭제한다() {
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
        // 삭제 대상이 정확히 조건이 깨진 그 자동기록 하나뿐임을 캡처해서 확인한다(다른 레코드는
        // findByScheduleIdAndAutoCategoryIsNotNull(10L)이 돌려준 existing 목록에 아예 없어 후보에도 오르지 않는다)
        ArgumentCaptor<LedgerEntry> deleteCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(1)).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).isSameAs(stalePaymentIncome);
    }

    @Test
    void sync_부분_납부면_수입_행은_생성하지_않고_지출_3줄만_생성한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        // 한 명만 입금 완료 — 지원금·버림 차액은 입금 여부와 무관하게 확정되지만 수입은 전원 완료가 필요하다
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, false));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isEqualTo(3);
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE,
                        LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE);
        assertThat(saved).noneMatch(entry -> entry.getAutoCategory() == LedgerAutoCategory.PAYMENT_INCOME);
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

    // 영수증은 등록됐지만 settlement_target이 0명인 차수는 findByScheduleForAssistant가 애초에
    // 그 행을 반환하지 않는다(SettlementTargetRepositoryImplTest.
    // findByScheduleForAssistant_영수증은_있지만_정산대상이_0명인_차수는_반환하지_않는다로 검증) —
    // 여기서는 그 결과를 받는 서비스가 나머지 정상 차수만으로 올바르게 합산하는지 확인한다
    @Test
    void sync_정상_차수와_0명_차수가_섞이면_정상_차수만_합산한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound roundWithTargets = roundWithReceipt(100L, schedule, 1, 20000, 0);
        // 영수증은 등록됐지만 정산 명단이 없는(0명) 차수 — 실제 쿼리라면 target이 전혀 없어
        // 아래 targets 목록에 나타나지 않는다
        roundWithReceipt(101L, schedule, 2, 30000, 0);
        List<SettlementTarget> targets = List.of(target(roundWithTargets, 10000, true), target(roundWithTargets, 10000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(10L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 0명 차수(30000원)는 전혀 반영되지 않고, 정상 차수(20000원)만 영수증 지출·수입 2줄로 생성된다
        assertThat(response.getCreatedCount()).isEqualTo(2);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(amountOf(captor.getAllValues(), LedgerAutoCategory.RECEIPT_EXPENSE)).isEqualTo(20000);
        assertThat(amountOf(captor.getAllValues(), LedgerAutoCategory.PAYMENT_INCOME)).isEqualTo(20000);
    }

    // 정산 대상이 전부 제외돼(decisions #31·#48) 0명이 된 일정은 findScheduleIdsByGroupTerm에
    // 더 이상 걸리지 않지만, 과거에 생성된 자동기록이 남아있다면 findAutoScheduleIds로 대상에
    // 포함돼 삭제까지 이어져야 한다
    @Test
    void sync_0명_차수만_남은_일정의_기존_자동기록은_삭제한다() {
        Schedule schedule = scheduleWithId(10L, "인원이_모두_빠진_일정", LocalDateTime.of(2026, 11, 20, 18, 0));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);

        LedgerEntry staleReceipt = existingAuto(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE,
                "인원이_모두_빠진_일정 영수증 지출", 20000, entryDate);
        LedgerEntry stalePaymentIncome = existingAuto(groupTerm, schedule, LedgerAutoCategory.PAYMENT_INCOME,
                "인원이_모두_빠진_일정 부원 납부", 20000, entryDate);
        List<LedgerEntry> existing = List.of(staleReceipt, stalePaymentIncome);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        // 정산 대상이 하나도 없어 findScheduleIdsByGroupTerm은 이 일정을 돌려주지 않는다
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of());
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(List.of());
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isZero();
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isEqualTo(2);
        verify(ledgerEntryRepository).delete(staleReceipt);
        verify(ledgerEntryRepository).delete(stalePaymentIncome);
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

    @Test
    void sync_한_번의_호출에서_생성_갱신_삭제가_모두_발생한다() {
        // 일정A: 자동기록이 전혀 없던 일정 — 새로 생성된다
        Schedule scheduleA = scheduleWithId(20L, "일정A_신규", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound roundA = roundWithReceipt(200L, scheduleA, 1, 20000, 0);
        List<SettlementTarget> targetsA = List.of(target(roundA, 10000, true), target(roundA, 10000, true));

        // 일정B: 기존 영수증 지출 금액이 바뀌어 갱신된다(수입 줄은 기존에 없어 새로 생성됨)
        Schedule scheduleB = scheduleWithId(21L, "일정B_변경", LocalDateTime.of(2026, 11, 21, 18, 0));
        ScheduleRound roundB = roundWithReceipt(201L, scheduleB, 1, 20000, 0);
        List<SettlementTarget> targetsB = List.of(target(roundB, 10000, true), target(roundB, 10000, true));
        LedgerEntry staleReceiptB = existingAuto(groupTerm, scheduleB, LedgerAutoCategory.RECEIPT_EXPENSE,
                "일정B_변경 영수증 지출", 5000, LocalDate.of(2026, 11, 21));

        // 일정C: 입금이 되돌려져 조건이 깨진 기존 수입 줄이 삭제된다
        Schedule scheduleC = scheduleWithId(22L, "일정C_삭제", LocalDateTime.of(2026, 11, 22, 18, 0));
        ScheduleRound roundC = roundWithReceipt(202L, scheduleC, 1, 20000, 0);
        List<SettlementTarget> targetsC = List.of(target(roundC, 10000, true), target(roundC, 10000, false));
        LedgerEntry stalePaymentIncomeC = existingAuto(groupTerm, scheduleC, LedgerAutoCategory.PAYMENT_INCOME,
                "일정C_삭제 부원 납부", 20000, LocalDate.of(2026, 11, 22));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).willReturn(List.of(20L, 21L, 22L));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of());
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(scheduleA));
        given(scheduleRepository.findById(21L)).willReturn(Optional.of(scheduleB));
        given(scheduleRepository.findById(22L)).willReturn(Optional.of(scheduleC));
        given(settlementTargetRepository.findByScheduleForAssistant(20L, null)).willReturn(targetsA);
        given(settlementTargetRepository.findByScheduleForAssistant(21L, null)).willReturn(targetsB);
        given(settlementTargetRepository.findByScheduleForAssistant(22L, null)).willReturn(targetsC);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(20L)).willReturn(List.of());
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(21L)).willReturn(List.of(staleReceiptB));
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(22L)).willReturn(List.of(stalePaymentIncomeC));

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 일정A: 영수증 지출·수입 2건 생성. 일정B: 수입 1건 생성. 일정C: 영수증 지출 1건 생성 → 합 4건
        assertThat(response.getCreatedCount()).isEqualTo(4);
        // 일정B의 영수증 지출 금액이 5000 → 20000으로 바뀌어 1건 갱신
        assertThat(response.getUpdatedCount()).isEqualTo(1);
        // 일정C는 부분 납부로 전원 완료가 깨져 기존 수입 줄이 삭제됨
        assertThat(response.getDeletedCount()).isEqualTo(1);

        assertThat(staleReceiptB.getAmount()).isEqualTo(20000);
        verify(ledgerEntryRepository).delete(stalePaymentIncomeC);
    }
}
