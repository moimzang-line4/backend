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
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
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
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerSyncService ledgerSyncService;

    private final GroupTerm groupTerm = groupTermWithId(1L);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ledgerSyncService = new LedgerSyncService(
                groupTermRepository, scheduleRepository, scheduleRoundRepository, settlementTargetRepository, ledgerEntryRepository);
        lenient().when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(settlementTargetRepository.findScheduleIdsByGroupTerm(1L)).thenReturn(List.of());
        lenient().when(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).thenReturn(List.of());
        lenient().when(ledgerEntryRepository.findAutoScheduleIds(1L)).thenReturn(List.of());
    }

    private GroupTerm groupTermWithId(Long id) {
        GroupTerm term = GroupTerm.create(mock(ClubGroup.class), "26기");
        ReflectionTestUtils.setField(term, "id", id);
        return term;
    }

    private Schedule scheduleWithId(Long id, String title, LocalDateTime startAt) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", id);
        ReflectionTestUtils.setField(schedule, "groupTerm", groupTerm);
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

    private int amountOf(List<LedgerEntry> entries, LedgerAutoCategory category) {
        return entries.stream().filter(e -> e.getAutoCategory() == category).findFirst().orElseThrow().getAmount();
    }

    private void stubSchedule(Long scheduleId, Schedule schedule, List<ScheduleRound> rounds, List<SettlementTarget> targets) {
        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(scheduleId)).willReturn(rounds);
        given(settlementTargetRepository.findByScheduleForAssistant(scheduleId, null)).willReturn(targets);
        lenient().when(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(scheduleId)).thenReturn(List.of());
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
    void sync_전원_입금_완료인_일정은_지출_2줄과_수입_1줄을_모두_생성한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        stubSchedule(10L, schedule, List.of(round), targets);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isEqualTo(3);
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE,
                        LedgerAutoCategory.PAYMENT_INCOME);

        // 영수증 지출 = total - subsidy(100원 버림 차액 포함), 지원금은 그대로, 수입은 전원 완료 시 billedAmount 합
        assertThat(amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE)).isEqualTo(40500);
        assertThat(amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE)).isEqualTo(10000);
        assertThat(amountOf(saved, LedgerAutoCategory.PAYMENT_INCOME)).isEqualTo(40400);

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

    @Test
    void sync_지원금이_0원이면_동아리_지원금_줄은_생성하지_않는다() {
        Schedule schedule = scheduleWithId(10L, "번개 모임", LocalDateTime.of(2026, 11, 21, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 40000, 0);
        List<SettlementTarget> targets = List.of(target(round, 20000, false), target(round, 20000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        stubSchedule(10L, schedule, List.of(round), targets);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 지원금 0원 → 그 줄만 미생성. 한 명 미입금이라 부원 납부 수입도 미생성. 영수증 지출만 생성
        assertThat(response.getCreatedCount()).isEqualTo(1);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getAutoCategory()).isEqualTo(LedgerAutoCategory.RECEIPT_EXPENSE);
        assertThat(captor.getValue().getAmount()).isEqualTo(40000);
    }

    @Test
    void sync_정산_명단이_없어도_영수증_지출과_지원금은_생성된다() {
        Schedule schedule = scheduleWithId(10L, "번개 모임", LocalDateTime.of(2026, 11, 21, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50000, 10000);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        // API-038(정산 명단 저장)을 아직 하지 않아 settlement_target이 하나도 없는 상태
        stubSchedule(10L, schedule, List.of(round), List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isEqualTo(2);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();
        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);
        assertThat(amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE)).isEqualTo(40000);
        assertThat(amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE)).isEqualTo(10000);
    }

    @Test
    void sync_재호출시_금액이_같으면_updatedCount에_포함하지_않는다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, true));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);

        List<LedgerEntry> existing = List.of(
                existingAuto(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE, "가을 공연 영수증 지출", 40500, entryDate),
                existingAuto(groupTerm, schedule, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, "가을 공연 동아리 지원금", 10000, entryDate),
                existingAuto(groupTerm, schedule, LedgerAutoCategory.PAYMENT_INCOME, "가을 공연 부원 납부", 40400, entryDate));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(round));
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
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(round));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getUpdatedCount()).isEqualTo(1);
        assertThat(staleReceiptEntry.getAmount()).isEqualTo(40500);
        // 나머지 2종(지원금·수입)은 기존에 없어 새로 생성됨
        assertThat(response.getCreatedCount()).isEqualTo(2);
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
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(round));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(existing);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getDeletedCount()).isEqualTo(1);
        ArgumentCaptor<LedgerEntry> deleteCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(1)).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).isSameAs(stalePaymentIncome);
    }

    @Test
    void sync_부분_납부면_수입_행은_생성하지_않고_지출_2줄만_생성한다() {
        Schedule schedule = scheduleWithId(10L, "가을 공연", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50500, 10000);
        List<SettlementTarget> targets = List.of(target(round, 20200, true), target(round, 20200, false));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        stubSchedule(10L, schedule, List.of(round), targets);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isEqualTo(2);
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);
        assertThat(saved).noneMatch(entry -> entry.getAutoCategory() == LedgerAutoCategory.PAYMENT_INCOME);
    }

    @Test
    void sync_영수증이_없는_차수의_정산대상은_수입_계산에서_전부_미반영한다() {
        Schedule schedule = scheduleWithId(10L, "영수증_미등록_일정", LocalDateTime.of(2026, 11, 22, 18, 0));
        ScheduleRound roundWithoutReceipt = roundWithoutReceipt(200L, schedule, 1);
        // 정산 대상이 있어도(비정상 상태를 방어적으로 가정) 영수증이 없는 차수는 수입 계산에서 미반영한다.
        // 영수증이 없으니 findScheduleIdsWithReceiptByGroupTerm에도 걸리지 않지만, 기존 자동기록이
        // 남아있을 수 있어 findAutoScheduleIds로 대상에 들어온다고 가정한다
        List<SettlementTarget> targets = List.of(target(roundWithoutReceipt, 10000, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(roundWithoutReceipt));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of());

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        assertThat(response.getCreatedCount()).isZero();
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(response.getDeletedCount()).isZero();
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void sync_여러_차수를_모두_합산한다() {
        Schedule schedule = scheduleWithId(10L, "MT", LocalDateTime.of(2026, 11, 23, 9, 0));
        ScheduleRound round1 = roundWithReceipt(100L, schedule, 1, 30300, 5000);
        ScheduleRound round2 = roundWithReceipt(101L, schedule, 2, 20100, 0);
        List<SettlementTarget> targets = List.of(
                target(round1, 12650, true), target(round1, 12650, true),
                target(round2, 10050, true), target(round2, 10050, true));

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L));
        stubSchedule(10L, schedule, List.of(round1, round2), targets);

        ledgerSyncService.sync(1L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        // 영수증 지출 = (30300-5000) + (20100-0) = 45400, 지원금 = 5000
        assertThat(amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE)).isEqualTo(45400);
        assertThat(amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE)).isEqualTo(5000);
        assertThat(amountOf(saved, LedgerAutoCategory.PAYMENT_INCOME)).isEqualTo(45400);
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
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(10L, 11L));
        stubSchedule(10L, schedule1, List.of(round1), targets1);
        stubSchedule(11L, schedule2, List.of(round2), targets2);

        LedgerSyncResponse response = ledgerSyncService.sync(1L);

        // 일정마다 영수증 지출 + 부원 납부 수입(지원금 0) = 2줄씩, 합쳐 4줄
        assertThat(response.getCreatedCount()).isEqualTo(4);
    }

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
        // 영수증도 삭제됐고 정산 대상도 없어, 영수증·정산대상 기반 쿼리에는 이 일정이 걸리지 않는다
        given(ledgerEntryRepository.findAutoScheduleIds(1L)).willReturn(List.of(10L));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of());
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
        given(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(1L)).willReturn(List.of(20L, 21L, 22L));
        stubSchedule(20L, scheduleA, List.of(roundA), targetsA);
        given(scheduleRepository.findById(21L)).willReturn(Optional.of(scheduleB));
        given(scheduleRoundRepository.findOrderedRounds(21L)).willReturn(List.of(roundB));
        given(settlementTargetRepository.findByScheduleForAssistant(21L, null)).willReturn(targetsB);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(21L)).willReturn(List.of(staleReceiptB));
        given(scheduleRepository.findById(22L)).willReturn(Optional.of(scheduleC));
        given(scheduleRoundRepository.findOrderedRounds(22L)).willReturn(List.of(roundC));
        given(settlementTargetRepository.findByScheduleForAssistant(22L, null)).willReturn(targetsC);
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

    @Test
    void syncSchedule_존재하지_않는_일정이면_조용히_무시한다() {
        given(scheduleRepository.findById(10L)).willReturn(Optional.empty());

        ledgerSyncService.syncSchedule(10L);

        verify(ledgerEntryRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).delete(any());
    }

    @Test
    void syncSchedule_영수증_등록_직후_호출하면_정산_명단_없이도_지출_2줄을_생성한다() {
        Schedule schedule = scheduleWithId(10L, "영수증_직후", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 50000, 10000);
        stubSchedule(10L, schedule, List.of(round), List.of());

        ledgerSyncService.syncSchedule(10L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();
        assertThat(saved).extracting(LedgerEntry::getAutoCategory)
                .containsExactlyInAnyOrder(LedgerAutoCategory.RECEIPT_EXPENSE, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE);
        assertThat(amountOf(saved, LedgerAutoCategory.RECEIPT_EXPENSE)).isEqualTo(40000);
        assertThat(amountOf(saved, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE)).isEqualTo(10000);
    }

    @Test
    void syncSchedule_전원_PAID로_바뀌면_수입_줄을_생성하고_termId를_몰라도_동작한다() {
        Schedule schedule = scheduleWithId(10L, "입금_확인_직후", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 20000, 0);
        List<SettlementTarget> targets = List.of(target(round, 10000, true), target(round, 10000, true));
        stubSchedule(10L, schedule, List.of(round), targets);

        ledgerSyncService.syncSchedule(10L);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();
        assertThat(amountOf(saved, LedgerAutoCategory.PAYMENT_INCOME)).isEqualTo(20000);
        verify(groupTermRepository, never()).findById(any());
    }

    @Test
    void syncSchedule_입금을_되돌리면_기존_수입_줄을_삭제한다() {
        Schedule schedule = scheduleWithId(10L, "입금_되돌리기_직후", LocalDateTime.of(2026, 11, 20, 18, 0));
        ScheduleRound round = roundWithReceipt(100L, schedule, 1, 20000, 0);
        List<SettlementTarget> targets = List.of(target(round, 10000, true), target(round, 10000, false));
        LocalDate entryDate = LocalDate.of(2026, 11, 20);
        LedgerEntry stalePaymentIncome = existingAuto(groupTerm, schedule, LedgerAutoCategory.PAYMENT_INCOME,
                "입금_되돌리기_직후 부원 납부", 20000, entryDate);

        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(round));
        given(settlementTargetRepository.findByScheduleForAssistant(10L, null)).willReturn(targets);
        given(ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(10L)).willReturn(List.of(stalePaymentIncome));

        ledgerSyncService.syncSchedule(10L);

        verify(ledgerEntryRepository).delete(stalePaymentIncome);
    }
}
