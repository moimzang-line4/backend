package com.moimzang.domain.budget.service;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
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

// 일정(schedule) 단위로 최대 2종 지출 + 1종 수입 자동 기록을 계산해 반영한다(decisions #63·#86).
// 지출 2종(영수증 지출·동아리 지원금)은 영수증 등록만으로 확정되므로 정산 명단과 무관하게 즉시
// 계산되고, 수입(부원 납부)은 여전히 정산 명단 전원 완료를 조건으로 한다. syncSchedule(scheduleId)는
// 영수증 등록·수정·삭제(API-034/035)·입금 확인·되돌리기(API-040) 직후 호출되는 즉시 재계산
// 진입점이고, sync(termId)는 API-074(정산 내역 새로고침)가 쓰는 수동 전체 재계산 진입점이다 — 둘 다
// 같은 계산 로직(syncSchedule 단일 일정 단위)을 공유한다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerSyncService {

    private final GroupTermRepository groupTermRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final SettlementTargetRepository settlementTargetRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerSyncResponse sync(Long termId) {
        log.info("[정산 내역 새로고침] 동기화를 시작합니다. termId = {}", termId);

        groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[정산 내역 새로고침] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        // 영수증이 등록된 일정(지출 2종의 근거), 정산 대상이 있는 일정(수입의 근거), 이미
        // 자동기록이 남아있는 일정(조건이 깨져 삭제해야 할 수 있는 일정)을 합친다 — 셋 다 termId로
        // 범위를 좁혀서, 다른 기수의 일정은 건드리지 않는다
        Set<Long> scheduleIds = new LinkedHashSet<>();
        scheduleIds.addAll(scheduleRoundRepository.findScheduleIdsWithReceiptByGroupTerm(termId));
        scheduleIds.addAll(settlementTargetRepository.findScheduleIdsByGroupTerm(termId));
        scheduleIds.addAll(ledgerEntryRepository.findAutoScheduleIds(termId));

        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (Long scheduleId : scheduleIds) {
            SyncCount count = syncScheduleInternal(scheduleId);
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

    // 일정 하나만 즉시 재계산한다(영수증 등록·수정·삭제, 입금 확인·되돌리기 직후 호출). 존재하지
    // 않는 일정이면 조용히 무시한다 — 호출 측이 이미 그 일정의 존재를 전제로 부른다
    @Transactional
    public void syncSchedule(Long scheduleId) {
        SyncCount count = syncScheduleInternal(scheduleId);
        log.info("[일정 자동기록 재계산] scheduleId = {}, createdCount = {}, updatedCount = {}, deletedCount = {}",
                scheduleId, count.created(), count.updated(), count.deleted());
    }

    private record SyncCount(int created, int updated, int deleted) {
    }

    // 일정 하나를 계산해 최대 3종 자동기록(지출 2종 + 수입 1종)을 upsert·삭제한다
    private SyncCount syncScheduleInternal(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return new SyncCount(0, 0, 0);
        }

        List<ScheduleRound> roundsWithReceipt = scheduleRoundRepository.findOrderedRounds(scheduleId).stream()
                .filter(ScheduleRound::hasReceipt)
                .toList();

        Map<LedgerAutoCategory, Integer> computedAmounts = computeAmounts(scheduleId, roundsWithReceipt);
        Map<LedgerAutoCategory, LedgerEntry> existingEntries = ledgerEntryRepository
                .findByScheduleIdAndAutoCategoryIsNotNull(scheduleId).stream()
                .collect(Collectors.toMap(LedgerEntry::getAutoCategory, Function.identity()));

        LocalDate entryDate = schedule.getStartAt().toLocalDate();
        GroupTerm groupTerm = schedule.getGroupTerm();

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

    // 영수증 지출(지원금을 뺀 나머지, 100원 버림 차액 포함)·동아리 지원금은 그 일정의 영수증이
    // 등록된 차수만 모으면 계산되고, 정산 명단(settlement_target) 유무와 무관하다. 부원 납부
    // 수입만 그 일정 전체 정산 대상이 1명 이상이고 전원 PAID일 때 billedAmount 합이다
    private Map<LedgerAutoCategory, Integer> computeAmounts(Long scheduleId, List<ScheduleRound> roundsWithReceipt) {
        int receiptExpense = 0;
        int clubSubsidyExpense = 0;
        for (ScheduleRound round : roundsWithReceipt) {
            int clubSubsidy = round.getClubSubsidyAmount() == null ? 0 : round.getClubSubsidyAmount();
            clubSubsidyExpense += clubSubsidy;
            receiptExpense += round.getTotalAmount() - clubSubsidy;
        }

        // 정산 대상이 없거나(API-038 미실행) 영수증이 없는 차수는 수입 계산에서 통째로
        // 제외한다(사용자 결정) — 현재 구현상 영수증 없이는 정산 대상이 생길 수 없어
        // (SettlementService.saveSettlementTargets가 SETTLEMENT_409_RECEIPT_NOT_REGISTERED로
        // 막는다) 이 필터는 방어적 안전장치다
        List<SettlementTarget> targets = settlementTargetRepository.findByScheduleForAssistant(scheduleId, null)
                .stream()
                .filter(target -> target.getScheduleRound().hasReceipt())
                .toList();
        boolean allPaid = !targets.isEmpty()
                && targets.stream().allMatch(target -> target.getPaymentStatus() == PaymentStatus.PAID);
        int paymentIncome = allPaid ? targets.stream().mapToInt(SettlementTarget::getBilledAmount).sum() : 0;

        Map<LedgerAutoCategory, Integer> amounts = new EnumMap<>(LedgerAutoCategory.class);
        amounts.put(LedgerAutoCategory.RECEIPT_EXPENSE, receiptExpense);
        amounts.put(LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, clubSubsidyExpense);
        amounts.put(LedgerAutoCategory.PAYMENT_INCOME, paymentIncome);
        return amounts;
    }
}
