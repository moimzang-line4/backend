package com.moimzang.domain.settlement.service;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetImportResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetListResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetSaveResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.domain.settlement.mapper.SettlementMapper;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
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
}
