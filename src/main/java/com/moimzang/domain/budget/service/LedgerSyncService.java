package com.moimzang.domain.budget.service;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.dto.response.LedgerSyncResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 정산 내역 새로고침(API-074). 일정 단위로 최대 4종 자동 기록(영수증 지출·동아리 지원금·추가
// 지원금·부원 납부 수입)을 계산해 반영한다(decisions #63). 전체를 하나의 트랜잭션으로 처리한다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerSyncService {

    private final GroupTermRepository groupTermRepository;
    private final ScheduleRepository scheduleRepository;
    private final SettlementTargetRepository settlementTargetRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerSyncResponse sync(Long termId) {
        log.info("[정산 내역 새로고침] 동기화를 시작합니다. termId = {}", termId);

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[정산 내역 새로고침] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        // 정산 대상이 있는 일정과, 이미 자동기록이 남아있는 일정(정산 대상이 전부 사라진 경우의
        // 삭제 판단용)을 합친다 — 둘 다 termId로 범위를 좁혀서, 다른 기수의 일정은 건드리지 않는다
        Set<Long> scheduleIds = new LinkedHashSet<>();
        scheduleIds.addAll(settlementTargetRepository.findScheduleIdsByGroupTerm(termId));
        scheduleIds.addAll(ledgerEntryRepository.findAutoScheduleIds(termId));

        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (Long scheduleId : scheduleIds) {
            SyncCount count = syncSchedule(groupTerm, scheduleId);
            created += count.created();
            updated += count.updated();
            deleted += count.deleted();
        }

        log.info("[정산 내역 새로고침] 동기화를 완료했습니다. termId = {}, createdCount = {}, updatedCount = {}, deletedCount = {}",
                termId, created, updated, deleted);
        return LedgerSyncResponse.builder()
                .createdCount(created)
                .updatedCount(updated)
                .deletedCount(deleted)
                .build();
    }

    private record SyncCount(int created, int updated, int deleted) {
    }

    // 일정 하나를 계산해 최대 4종 자동기록을 upsert·삭제한다
    private SyncCount syncSchedule(GroupTerm groupTerm, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return new SyncCount(0, 0, 0);
        }

        // 정산 대상이 없거나(API-038 미실행) 영수증이 없는 차수는 명단 0명 차수와 같은 취지로
        // 통째로 미반영한다(사용자 결정) — 현재 구현상 영수증 없이는 정산 대상이 생길 수 없어
        // (SettlementService.saveSettlementTargets가 SETTLEMENT_409_RECEIPT_NOT_REGISTERED로
        // 막는다) 이 필터는 방어적 안전장치다
        List<SettlementTarget> targets = settlementTargetRepository.findByScheduleForAssistant(scheduleId, null)
                .stream()
                .filter(target -> target.getScheduleRound().hasReceipt())
                .toList();

        Map<LedgerAutoCategory, Integer> computedAmounts = computeAmounts(targets);
        Map<LedgerAutoCategory, LedgerEntry> existingEntries = ledgerEntryRepository
                .findByScheduleIdAndAutoCategoryIsNotNull(scheduleId).stream()
                .collect(Collectors.toMap(LedgerEntry::getAutoCategory, Function.identity()));

        LocalDate entryDate = schedule.getStartAt().toLocalDate();

        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (LedgerAutoCategory category : LedgerAutoCategory.values()) {
            Integer amount = computedAmounts.get(category);
            LedgerEntry existingEntry = existingEntries.get(category);

            // 금액이 0원(이하)인 줄은 생성하지 않고, 이미 있던 줄은 조건이 깨진 것이므로 삭제한다
            if (amount == null || amount <= 0) {
                if (existingEntry != null) {
                    ledgerEntryRepository.delete(existingEntry);
                    deleted++;
                }
                continue;
            }

            String itemName = schedule.getTitle() + " " + category.label();
            if (existingEntry == null) {
                ledgerEntryRepository.save(LedgerEntry.createAuto(groupTerm, schedule, category, itemName, amount, entryDate));
                created++;
            } else if (hasChanged(existingEntry, itemName, amount, entryDate)) {
                existingEntry.update(category.type(), itemName, amount, entryDate);
                updated++;
            }
        }

        return new SyncCount(created, updated, deleted);
    }

    // 값이 실제로 바뀐 경우에만 updatedCount에 포함한다(사용자 결정)
    private boolean hasChanged(LedgerEntry entry, String itemName, Integer amount, LocalDate entryDate) {
        return !entry.getItemName().equals(itemName)
                || !entry.getAmount().equals(amount)
                || !entry.getEntryDate().equals(entryDate);
    }

    // 차수별로 묶어 영수증 지출(billedAmount 합)·동아리 지원금(club_subsidy_amount 합)·추가
    // 지원금(100원 버림 차액 합)을 계산한다. billedAmount는 현재 구현상 한 차수 안에서 전원 같은
    // 값이지만(면제 등 개별 조정 경로가 없다), 그룹 내 합계로 계산해 둔다. 부원 납부 수입은 이
    // 리스트(영수증이 없는 차수가 걸러진 뒤) 전체가 전원 입금 완료일 때만 billedAmount 합이다
    private Map<LedgerAutoCategory, Integer> computeAmounts(List<SettlementTarget> targets) {
        Map<Long, List<SettlementTarget>> byRound = targets.stream()
                .collect(Collectors.groupingBy(target -> target.getScheduleRound().getId()));

        int receiptExpense = 0;
        int clubSubsidyExpense = 0;
        int roundingSubsidyExpense = 0;
        for (List<SettlementTarget> roundTargets : byRound.values()) {
            ScheduleRound round = roundTargets.get(0).getScheduleRound();
            int clubSubsidy = round.getClubSubsidyAmount() == null ? 0 : round.getClubSubsidyAmount();
            int billedSum = roundTargets.stream().mapToInt(SettlementTarget::getBilledAmount).sum();

            clubSubsidyExpense += clubSubsidy;
            receiptExpense += billedSum;
            roundingSubsidyExpense += round.getTotalAmount() - clubSubsidy - billedSum;
        }

        boolean allPaid = !targets.isEmpty()
                && targets.stream().allMatch(target -> target.getPaymentStatus() == PaymentStatus.PAID);
        int paymentIncome = allPaid ? targets.stream().mapToInt(SettlementTarget::getBilledAmount).sum() : 0;

        Map<LedgerAutoCategory, Integer> amounts = new EnumMap<>(LedgerAutoCategory.class);
        amounts.put(LedgerAutoCategory.RECEIPT_EXPENSE, receiptExpense);
        amounts.put(LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, clubSubsidyExpense);
        amounts.put(LedgerAutoCategory.ROUNDING_SUBSIDY_EXPENSE, roundingSubsidyExpense);
        amounts.put(LedgerAutoCategory.PAYMENT_INCOME, paymentIncome);
        return amounts;
    }
}
