package com.moimzang.domain.settlement.service;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.domain.settlement.dto.request.AccountSetRequest;
import com.moimzang.domain.settlement.dto.request.PaymentConfirmRequest;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementNoticeRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.AccountResponse;
import com.moimzang.domain.settlement.dto.response.PaymentStatusResponse;
import com.moimzang.domain.settlement.dto.response.PaymentSummaryListResponse;
import com.moimzang.domain.settlement.dto.response.PaymentSummaryResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeRoundResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetImportResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetListResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetSaveResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.enums.PaymentStatusFilter;
import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.domain.settlement.mapper.SettlementMapper;
import com.moimzang.domain.settlement.repository.PaymentSummary;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final ScheduleRepository scheduleRepository;
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

    // 정산 명단을 조회한다. targetCount는 그 차수 전체 인원 수(페이지네이션용 totalCount 아님)이고,
    // billedAmount는 명단 전원이 같은 값이라 1건만 조회해 대표로 쓴다
    public SettlementTargetListResponse getSettlementTargets(Long roundId, String keyword, String cursor, Integer size) {
        log.info("[정산 명단 조회] 조회를 시작합니다. roundId = {}, keyword = {}, cursor = {}", roundId, keyword, cursor);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[정산 명단 조회] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        int limit = CursorPageConstants.resolveSize(size);
        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorId = decoded.getId();
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(decoded.getSortValue()))
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (RuntimeException e) {
                log.warn("[정산 명단 조회] cursor 형식이 올바르지 않습니다. roundId = {}, cursor = {}", roundId, cursor);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<SettlementTarget> fetched = settlementTargetRepository.search(roundId, keyword, cursorCreatedAt, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<SettlementTarget> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<SettlementTargetResponse> targets = page.stream()
                .map(settlementMapper::toSettlementTargetResponse)
                .toList();
        String nextCursor = hasNext ? buildCursor(page.get(page.size() - 1)) : null;

        long targetCount = settlementTargetRepository.countByScheduleRoundId(roundId);
        Integer billedAmount = settlementTargetRepository.findFirstByScheduleRoundId(roundId)
                .map(SettlementTarget::getBilledAmount)
                .orElse(0);

        log.info("[정산 명단 조회] 조회를 완료했습니다. roundId = {}, targetCount = {}, hasNext = {}", roundId, targetCount, hasNext);
        return SettlementTargetListResponse.builder()
                .label(round.getLabel())
                .targetCount(targetCount)
                .billedAmount(billedAmount)
                .targets(targets)
                .nextCursor(nextCursor)
                .build();
    }

    private String buildCursor(SettlementTarget last) {
        String sortValue = String.valueOf(last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return CursorCodec.encode(sortValue, last.getId());
    }

    // 보낸 attendeeIds로 정산 명단을 통째로 교체한다. 기존-신규를 diff해서 유지되는 대상은
    // 금액만 갱신하고, 빠지는 대상은 삭제하고, 새로 들어온 대상만 추가한다(Hibernate가 flush
    // 시 insert를 delete보다 먼저 실행해 "전체 삭제 후 재등록"은 같은 조합 재삽입 시 유니크
    // 제약과 충돌할 수 있다 — diff 방식은 같은 조합을 지웠다가 다시 넣는 일이 없어 안전하다)
    @Transactional
    public SettlementTargetSaveResponse saveSettlementTargets(Long roundId, SettlementTargetSaveRequest request) {
        log.info("[정산 명단 저장] 저장을 시작합니다. roundId = {}, attendeeIds = {}", roundId, request.getAttendeeIds());

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[정산 명단 저장] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        List<Long> distinctAttendeeIds = request.getAttendeeIds().stream().distinct().toList();
        if (distinctAttendeeIds.isEmpty()) {
            log.warn("[정산 명단 저장] 명단이 비어있습니다. roundId = {}", roundId);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        if (!round.hasReceipt()) {
            log.warn("[정산 명단 저장] 영수증이 등록되지 않았습니다. roundId = {}", roundId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_409_RECEIPT_NOT_REGISTERED);
        }

        List<ScheduleAttendee> attendees = scheduleAttendeeRepository.findAllById(distinctAttendeeIds);
        boolean hasInvalidAttendee = attendees.size() != distinctAttendeeIds.size()
                || attendees.stream().anyMatch(attendee -> !attendee.getSchedule().getId().equals(round.getSchedule().getId()));
        if (hasInvalidAttendee) {
            log.warn("[정산 명단 저장] 그 일정 소속이 아닌 참가자가 섞여 있습니다. roundId = {}", roundId);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        List<SettlementTarget> existingTargets = settlementTargetRepository.findByScheduleRoundId(roundId);
        Map<Long, SettlementTarget> existingByAttendeeId = existingTargets.stream()
                .collect(Collectors.toMap(target -> target.getScheduleAttendee().getId(), Function.identity()));

        Set<Long> requestedIds = new HashSet<>(distinctAttendeeIds);
        List<SettlementTarget> toRemove = existingTargets.stream()
                .filter(target -> !requestedIds.contains(target.getScheduleAttendee().getId()))
                .toList();
        boolean removesPaidMember = toRemove.stream().anyMatch(target -> target.getPaymentStatus() == PaymentStatus.PAID);
        if (removesPaidMember) {
            log.warn("[정산 명단 저장] 입금 완료된 인원을 제외할 수 없습니다. roundId = {}", roundId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_423_PAID_MEMBER_LOCKED);
        }

        int billedAmount = billedAmountCalculator.calculate(round.getTotalAmount(), round.getClubSubsidyAmount(), distinctAttendeeIds.size());
        Map<Long, ScheduleAttendee> attendeeById = attendees.stream()
                .collect(Collectors.toMap(ScheduleAttendee::getId, Function.identity()));

        for (Long attendeeId : distinctAttendeeIds) {
            SettlementTarget existing = existingByAttendeeId.get(attendeeId);
            if (existing != null) {
                existing.updateBilledAmount(billedAmount);
            } else {
                settlementTargetRepository.save(SettlementTarget.create(round, attendeeById.get(attendeeId), billedAmount));
            }
        }
        settlementTargetRepository.deleteAll(toRemove);

        log.info("[정산 명단 저장] 저장을 완료했습니다. roundId = {}, targetCount = {}, billedAmount = {}",
                roundId, distinctAttendeeIds.size(), billedAmount);
        return settlementMapper.toSettlementTargetSaveResponse(round, distinctAttendeeIds.size(), billedAmount);
    }

    // 그 차수의 출석 완료자를 미리보기로 반환한다(저장 안 함). expectedBilledAmount는 출석 완료자
    // 전원 기준으로 계산한 미리보기 값이고, 출석 완료자가 없으면 계산기를 호출하지 않는다(decisions #31)
    public SettlementTargetImportResponse importSettlementTargets(Long roundId) {
        log.info("[행사 명단 불러오기] 조회를 시작합니다. roundId = {}", roundId);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[행사 명단 불러오기] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        if (!round.hasReceipt()) {
            log.warn("[행사 명단 불러오기] 영수증이 등록되지 않았습니다. roundId = {}", roundId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_409_RECEIPT_NOT_REGISTERED);
        }

        List<RoundAttendee> checkedInAttendees = roundAttendeeRepository.findCheckedInByScheduleRoundId(roundId);
        int expectedBilledAmount = checkedInAttendees.isEmpty() ? 0
                : billedAmountCalculator.calculate(round.getTotalAmount(), round.getClubSubsidyAmount(), checkedInAttendees.size());

        log.info("[행사 명단 불러오기] 조회를 완료했습니다. roundId = {}, count = {}", roundId, checkedInAttendees.size());
        return SettlementTargetImportResponse.builder()
                .attendees(checkedInAttendees.stream().map(settlementMapper::toImportAttendeeResponse).toList())
                .expectedBilledAmount(expectedBilledAmount)
                .build();
    }

    // 그 사람의 일정 내 모든 차수 settlement_target을 함께 갱신한다(장부 반영은 API-074 담당).
    // 이미 같은 상태인 차수는 멱등하게 건드리지 않아 그 차수의 기존 paidAt이 유지된다
    // (RoundAttendee.updateCheckedIn과 같은 기준)
    @Transactional
    public PaymentStatusResponse updatePaymentStatus(Long scheduleId, Long attendeeId, PaymentConfirmRequest request) {
        log.info("[입금 확인·되돌리기] 처리를 시작합니다. scheduleId = {}, attendeeId = {}, paid = {}",
                scheduleId, attendeeId, request.getPaid());

        List<SettlementTarget> targets = settlementTargetRepository.findByScheduleAndAttendee(scheduleId, attendeeId);
        if (targets.isEmpty()) {
            log.warn("[입금 확인·되돌리기] 그 일정에 정산 대상이 없는 참가자입니다. scheduleId = {}, attendeeId = {}", scheduleId, attendeeId);
            throw new CustomException(CommonErrorCode.NOT_FOUND);
        }

        boolean paid = request.getPaid();
        targets.forEach(target -> target.updatePaymentStatus(paid));

        ScheduleAttendee attendee = targets.get(0).getScheduleAttendee();
        PaymentStatus paymentStatus = paid ? PaymentStatus.PAID : PaymentStatus.UNPAID;
        // 여러 차수 중 일부는 이전부터 이미 그 상태였을 수 있어(멱등) paidAt이 차수마다 다를 수
        // 있다 — 가장 최근 시각을 대표로 내려준다. 되돌리기는 전 차수가 null이라 자연히 null이다
        LocalDateTime paidAt = paid
                ? targets.stream().map(SettlementTarget::getPaidAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null)
                : null;

        log.info("[입금 확인·되돌리기] 처리를 완료했습니다. scheduleId = {}, attendeeId = {}, paymentStatus = {}",
                scheduleId, attendeeId, paymentStatus);
        return settlementMapper.toPaymentStatusResponse(attendee, paymentStatus, paidAt);
    }

    // 일정의 모든 차수에서 그 사람의 정산 대상을 삭제하고, 각 차수의 남은 인원으로 1인 청구액을
    // 재계산한다. 한 차수라도 PAID면 423으로 거부한다
    @Transactional
    public void removeFromPayments(Long scheduleId, Long attendeeId) {
        log.info("[입금 명단 삭제] 삭제를 시작합니다. scheduleId = {}, attendeeId = {}", scheduleId, attendeeId);

        List<SettlementTarget> targets = settlementTargetRepository.findByScheduleAndAttendee(scheduleId, attendeeId);
        if (targets.isEmpty()) {
            log.warn("[입금 명단 삭제] 그 일정에 정산 대상이 없는 참가자입니다. scheduleId = {}, attendeeId = {}", scheduleId, attendeeId);
            throw new CustomException(CommonErrorCode.NOT_FOUND);
        }

        boolean hasPaidRound = targets.stream().anyMatch(target -> target.getPaymentStatus() == PaymentStatus.PAID);
        if (hasPaidRound) {
            log.warn("[입금 명단 삭제] 입금 완료된 차수가 있어 삭제할 수 없습니다. scheduleId = {}, attendeeId = {}", scheduleId, attendeeId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_423_PAID_CANNOT_DELETE);
        }

        // 삭제 후 조회하면 flush 시점에 따라 결과가 꼬일 수 있어, 차수별 남은 인원을 삭제 전에
        // 먼저 계산해 둔다(decisions #31: 남은 인원이 0명이면 계산기를 호출하지 않는다)
        Map<Long, List<SettlementTarget>> remainingByRoundId = new HashMap<>();
        for (SettlementTarget target : targets) {
            Long roundId = target.getScheduleRound().getId();
            List<SettlementTarget> remaining = settlementTargetRepository.findByScheduleRoundId(roundId).stream()
                    .filter(roundTarget -> !roundTarget.getId().equals(target.getId()))
                    .toList();
            remainingByRoundId.put(roundId, remaining);
        }

        settlementTargetRepository.deleteAll(targets);

        for (SettlementTarget target : targets) {
            List<SettlementTarget> remaining = remainingByRoundId.get(target.getScheduleRound().getId());
            if (remaining.isEmpty()) {
                continue;
            }
            ScheduleRound round = target.getScheduleRound();
            int billedAmount = billedAmountCalculator.calculate(round.getTotalAmount(), round.getClubSubsidyAmount(), remaining.size());
            remaining.forEach(remainingTarget -> remainingTarget.updateBilledAmount(billedAmount));
        }

        log.info("[입금 명단 삭제] 삭제를 완료했습니다. scheduleId = {}, attendeeId = {}, roundCount = {}",
                scheduleId, attendeeId, targets.size());
    }

    // 일정의 입금 현황을 조회한다. status는 사람 단위 집계(모든 차수가 PAID여야 PAID) 기준이고,
    // totalBilledAmount는 그 사람의 일정 내 모든 차수 billedAmount 합산이다
    public PaymentSummaryListResponse getPaymentSummaries(Long scheduleId, String status, String keyword, String cursor, Integer size) {
        log.info("[입금 현황 조회] 조회를 시작합니다. scheduleId = {}, status = {}, keyword = {}, cursor = {}",
                scheduleId, status, keyword, cursor);

        scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[입금 현황 조회] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        PaymentStatusFilter resolvedStatus = parseStatus(status);
        int limit = CursorPageConstants.resolveSize(size);

        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorId = decoded.getId();
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(decoded.getSortValue()))
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (RuntimeException e) {
                log.warn("[입금 현황 조회] cursor 형식이 올바르지 않습니다. scheduleId = {}, cursor = {}", scheduleId, cursor);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<PaymentSummary> fetched = settlementTargetRepository.searchPaymentSummaries(
                scheduleId, resolvedStatus, keyword, cursorCreatedAt, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<PaymentSummary> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<PaymentSummaryResponse> payments = page.stream()
                .map(settlementMapper::toPaymentSummaryResponse)
                .toList();
        String nextCursor = hasNext ? buildPaymentCursor(page.get(page.size() - 1)) : null;

        log.info("[입금 현황 조회] 조회를 완료했습니다. scheduleId = {}, count = {}, hasNext = {}", scheduleId, payments.size(), hasNext);
        return PaymentSummaryListResponse.builder()
                .payments(payments)
                .nextCursor(nextCursor)
                .build();
    }

    private PaymentStatusFilter parseStatus(String status) {
        if (status == null) {
            return PaymentStatusFilter.UNPAID;
        }
        try {
            return PaymentStatusFilter.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("[입금 현황 조회] status 값이 올바르지 않습니다. status = {}", status);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }
    }

    private String buildPaymentCursor(PaymentSummary last) {
        LocalDateTime createdAt = last.attendee().getCreatedAt();
        String sortValue = String.valueOf(createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return CursorCodec.encode(sortValue, last.attendee().getId());
    }

    // 그 차수의 입금 안내 계좌를 등록·수정한다(upsert, 은행 연동 없이 공지에 띄울 텍스트일 뿐)
    @Transactional
    public AccountResponse setAccount(Long roundId, AccountSetRequest request) {
        log.info("[입금 안내 계좌 설정] 설정을 시작합니다. roundId = {}", roundId);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[입금 안내 계좌 설정] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        round.updateAccount(request.getBankName(), request.getAccountNumber(), request.getAccountHolder());

        log.info("[입금 안내 계좌 설정] 설정을 완료했습니다. roundId = {}", roundId);
        return settlementMapper.toAccountResponse(round);
    }

    // 선택한 차수들의 정산 공지 문구를 만든다(저장하지 않고 응답만 생성). 대표 계좌는 선택 차수 중
    // roundNo가 가장 작은 차수의 계좌를 쓴다 — 요청 roundIds 배열 순서와 무관하게 결정적이기 때문이다
    public SettlementNoticeResponse generateSettlementNotice(Long scheduleId, SettlementNoticeRequest request) {
        log.info("[정산 공지 생성] 생성을 시작합니다. scheduleId = {}, roundIds = {}", scheduleId, request.getRoundIds());

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[정산 공지 생성] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        List<Long> distinctRoundIds = request.getRoundIds().stream().distinct().toList();
        if (distinctRoundIds.isEmpty()) {
            log.warn("[정산 공지 생성] 선택한 차수가 없습니다. scheduleId = {}", scheduleId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_400_ROUND_SELECTION_REQUIRED);
        }

        List<ScheduleRound> rounds = scheduleRoundRepository.findAllById(distinctRoundIds);
        boolean hasInvalidRound = rounds.size() != distinctRoundIds.size()
                || rounds.stream().anyMatch(round -> !round.getSchedule().getId().equals(scheduleId));
        if (hasInvalidRound) {
            log.warn("[정산 공지 생성] 그 일정 소속이 아닌 차수가 섞여 있습니다. scheduleId = {}", scheduleId);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        boolean hasUnregisteredReceipt = rounds.stream().anyMatch(round -> !round.hasReceipt());
        if (hasUnregisteredReceipt) {
            log.warn("[정산 공지 생성] 영수증이 등록되지 않은 차수가 포함되어 있습니다. scheduleId = {}", scheduleId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED);
        }

        boolean hasMissingAccount = rounds.stream().anyMatch(round -> !round.hasAccount());
        if (hasMissingAccount) {
            log.warn("[정산 공지 생성] 입금 안내 계좌가 설정되지 않은 차수가 포함되어 있습니다. scheduleId = {}", scheduleId);
            throw new CustomException(SettlementErrorCode.SETTLEMENT_409_ACCOUNT_NOT_SET);
        }

        // roundNo 오름차순으로 고정해 공지 문구 나열 순서·대표 계좌 선택이 요청 배열 순서에
        // 좌우되지 않게 한다
        List<ScheduleRound> orderedRounds = rounds.stream()
                .sorted(Comparator.comparing(ScheduleRound::getRoundNo))
                .toList();

        List<RoundAmount> roundAmounts = new ArrayList<>();
        for (ScheduleRound round : orderedRounds) {
            // 영수증은 등록됐지만 정산 명단(API-038)을 아직 저장하지 않은 차수는 영수증 미등록과
            // 같은 409로 거부한다 — 둘 다 "정산 준비가 안 된 차수"라는 같은 의미이기 때문이다
            Integer billedAmount = settlementTargetRepository.findFirstByScheduleRoundId(round.getId())
                    .map(SettlementTarget::getBilledAmount)
                    .orElseThrow(() -> {
                        log.warn("[정산 공지 생성] 정산 명단이 없는 차수가 포함되어 있습니다. scheduleId = {}, roundId = {}",
                                scheduleId, round.getId());
                        return new CustomException(SettlementErrorCode.SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED);
                    });
            roundAmounts.add(new RoundAmount(round, billedAmount));
        }

        int combinedAmount = roundAmounts.stream().mapToInt(RoundAmount::billedAmount).sum();
        ScheduleRound representativeRound = orderedRounds.get(0);
        String noticeText = buildNoticeText(schedule.getTitle(), roundAmounts, combinedAmount, representativeRound);

        log.info("[정산 공지 생성] 생성을 완료했습니다. scheduleId = {}, roundCount = {}, combinedAmount = {}",
                scheduleId, roundAmounts.size(), combinedAmount);
        return SettlementNoticeResponse.builder()
                .scheduleTitle(schedule.getTitle())
                .rounds(roundAmounts.stream()
                        .map(ra -> settlementMapper.toNoticeRoundResponse(ra.round(), ra.billedAmount()))
                        .toList())
                .combinedAmount(combinedAmount)
                .account(settlementMapper.toNoticeAccountResponse(representativeRound))
                .noticeText(noticeText)
                .build();
    }

    private record RoundAmount(ScheduleRound round, int billedAmount) {
    }

    private String buildNoticeText(String scheduleTitle, List<RoundAmount> roundAmounts, int combinedAmount,
                                    ScheduleRound representativeRound) {
        StringBuilder text = new StringBuilder();
        text.append("[").append(scheduleTitle).append("] 정산 안내\n\n");
        for (RoundAmount roundAmount : roundAmounts) {
            text.append(roundAmount.round().getLabel()).append(": ")
                    .append(formatAmount(roundAmount.billedAmount())).append("원\n");
        }
        text.append("\n총 입금액: ").append(formatAmount(combinedAmount)).append("원\n");
        text.append(representativeRound.getBankName()).append(" ").append(representativeRound.getAccountNumber())
                .append(" (").append(representativeRound.getAccountHolder()).append(")\n\n");
        text.append("확인 후 입금해주세요!");
        return text.toString();
    }

    private String formatAmount(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }
}
