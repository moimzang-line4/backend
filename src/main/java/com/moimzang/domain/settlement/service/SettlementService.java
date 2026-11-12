package com.moimzang.domain.settlement.service;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.domain.settlement.mapper.SettlementMapper;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementTargetRepository settlementTargetRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final RoundAttendeeRepository roundAttendeeRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SettlementMapper settlementMapper;
    private final ReceiptAmountValidator receiptAmountValidator;
    private final BilledAmountCalculator billedAmountCalculator;
    private final FileStorage fileStorage;

    // 영수증을 조회한다. isLocked는 그 차수에 입금 완료가 1명이라도 있으면 true다
    public ReceiptResponse getReceipt(Long roundId) {
        log.info("[영수증 조회] 조회를 시작합니다. roundId = {}", roundId);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[영수증 조회] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        boolean isLocked = settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(roundId, PaymentStatus.PAID);

        log.info("[영수증 조회] 조회를 완료했습니다. roundId = {}, isLocked = {}", roundId, isLocked);
        return settlementMapper.toReceiptResponse(round, isLocked);
    }

    // 영수증을 등록·수정한다(upsert, decisions #41). 입금 완료자가 있으면 423으로 거부하고,
    // 금액이 바뀌면 그 차수에 이미 저장된 정산 명단의 1인 청구액도 재계산한다
    @Transactional
    public ReceiptRegisterResponse registerReceipt(Long roundId, ReceiptRegisterRequest request) {
        log.info("[영수증 등록] 등록을 시작합니다. roundId = {}, totalAmount = {}, clubSubsidyAmount = {}",
                roundId, request.getTotalAmount(), request.getClubSubsidyAmount());

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[영수증 등록] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        receiptAmountValidator.validate(request.getTotalAmount(), request.getClubSubsidyAmount());

        boolean isLocked = settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(roundId, PaymentStatus.PAID);
        if (isLocked) {
            log.warn("[영수증 등록] 입금 완료자가 있어 수정할 수 없습니다. roundId = {}", roundId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT);
        }

        boolean amountChanged = !Objects.equals(request.getTotalAmount(), round.getTotalAmount())
                || !Objects.equals(request.getClubSubsidyAmount(), round.getClubSubsidyAmount());

        // imageKey를 생략하면 기존 이미지를 그대로 유지한다(decisions #41)
        String imageUrl = request.getImageKey() != null
                ? fileStorage.toUrlOrNull(request.getImageKey())
                : round.getImageUrl();

        round.registerReceipt(request.getStoreName(), request.getTransactedAt(),
                request.getTotalAmount(), request.getClubSubsidyAmount(), imageUrl);

        if (amountChanged) {
            recalculateBilledAmounts(roundId, request.getTotalAmount(), request.getClubSubsidyAmount());
        }

        log.info("[영수증 등록] 등록을 완료했습니다. roundId = {}, amountChanged = {}", roundId, amountChanged);
        return settlementMapper.toReceiptRegisterResponse(round);
    }

    // 영수증을 삭제한다(사실상 그 차수 자체를 cascade 삭제). 입금 완료자가 있으면 423으로 거부하고,
    // settlement_target·round_attendee는 함께 지우지만 ledger_entry는 참조만 NULL로 돌려 보존한다
    @Transactional
    public void deleteReceipt(Long roundId) {
        log.info("[영수증 삭제] 삭제를 시작합니다. roundId = {}", roundId);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[영수증 삭제] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        boolean isLocked = settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(roundId, PaymentStatus.PAID);
        if (isLocked) {
            log.warn("[영수증 삭제] 입금 완료 내역이 있어 삭제할 수 없습니다. roundId = {}", roundId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT);
        }

        settlementTargetRepository.deleteByScheduleRoundId(roundId);
        roundAttendeeRepository.deleteByScheduleRoundId(roundId);
        ledgerEntryRepository.clearScheduleRoundReference(roundId);
        scheduleRoundRepository.delete(round);

        log.info("[영수증 삭제] 삭제를 완료했습니다. roundId = {}", roundId);
    }

    // 금액 변경 시 그 차수에 이미 저장된 정산 명단 전원의 1인 청구액을 다시 계산한다.
    // 아직 정산 명단이 없으면(targets가 비어있으면) 계산기를 호출하지 않는다(decisions #31)
    private void recalculateBilledAmounts(Long roundId, int totalAmount, int clubSubsidyAmount) {
        List<SettlementTarget> targets = settlementTargetRepository.findByScheduleRoundId(roundId);
        if (targets.isEmpty()) {
            return;
        }

        int billedAmount = billedAmountCalculator.calculate(totalAmount, clubSubsidyAmount, targets.size());
        targets.forEach(target -> target.updateBilledAmount(billedAmount));
    }
}
