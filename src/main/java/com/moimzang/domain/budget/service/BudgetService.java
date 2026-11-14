package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryOriginResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.budget.exception.BudgetErrorCode;
import com.moimzang.domain.budget.mapper.BudgetMapper;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final GroupTermRepository groupTermRepository;
    private final BudgetMapper budgetMapper;

    // 장부에 직접 입력한다. auto_category·schedule·scheduleRound는 전부 null이다
    @Transactional
    public LedgerEntryCreateResponse createLedgerEntry(Long termId, LedgerEntryCreateRequest request) {
        log.info("[장부 직접 입력] 입력을 시작합니다. termId = {}, type = {}, amount = {}", termId, request.getType(), request.getAmount());

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[장부 직접 입력] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        LedgerEntry entry = ledgerEntryRepository.save(
                LedgerEntry.create(groupTerm, request.getType(), request.getItemName(), request.getAmount(), request.getEntryDate()));

        log.info("[장부 직접 입력] 입력을 완료했습니다. termId = {}, entryId = {}", termId, entry.getId());
        return budgetMapper.toCreateResponse(entry);
    }

    // 장부 내역 상세를 조회한다. source는 auto_category 유무로 판별하고(decisions #65), origin은
    // 자동 기록이면서 출처 일정이 살아있을 때만 채운다 — 직접 입력과 고아 자동 기록은 둘 다 null이다
    // (decisions #66, 구현 전 사용자 결정 — 고아는 source만으로 구분 가능해 origin을 null로 통일)
    public LedgerEntryDetailResponse getLedgerEntry(Long entryId) {
        log.info("[장부 내역 상세 조회] 조회를 시작합니다. entryId = {}", entryId);

        LedgerEntry entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> {
                    log.warn("[장부 내역 상세 조회] 존재하지 않는 내역입니다. entryId = {}", entryId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        LedgerSource source = entry.isAutoEntry() ? LedgerSource.RECEIPT : LedgerSource.MANUAL;
        LedgerEntryOriginResponse origin = entry.isAutoEntry() && entry.getSchedule() != null
                ? budgetMapper.toOriginResponse(entry.getSchedule())
                : null;

        log.info("[장부 내역 상세 조회] 조회를 완료했습니다. entryId = {}, source = {}", entryId, source);
        return budgetMapper.toDetailResponse(entry, source, origin);
    }

    // 직접 입력 내역만 부분 수정한다(보낸 필드만 반영). 자동 기록은 고아 여부와 무관하게 항상 거부한다
    @Transactional
    public LedgerEntryUpdateResponse updateLedgerEntry(Long entryId, LedgerEntryUpdateRequest request) {
        log.info("[장부 내역 수정] 수정을 시작합니다. entryId = {}", entryId);

        LedgerEntry entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> {
                    log.warn("[장부 내역 수정] 존재하지 않는 내역입니다. entryId = {}", entryId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        if (entry.isAutoEntry()) {
            log.warn("[장부 내역 수정] 자동 기록은 수정할 수 없습니다. entryId = {}", entryId);
            throw new CustomException(BudgetErrorCode.LEDGER_409_AUTO_ENTRY_LOCKED);
        }

        entry.update(request.getType(), request.getItemName(), request.getAmount(), request.getEntryDate());

        log.info("[장부 내역 수정] 수정을 완료했습니다. entryId = {}", entryId);
        return budgetMapper.toUpdateResponse(entry);
    }

    // 직접 입력은 항상 삭제 가능하다. 자동 기록은 출처 일정(schedule)이 살아있으면 재계산과
    // 충돌해 거부하고, 일정이 삭제되어 고아가 된 뒤에는 일반 항목처럼 삭제할 수 있다(decisions #66)
    @Transactional
    public void deleteLedgerEntry(Long entryId) {
        log.info("[장부 내역 삭제] 삭제를 시작합니다. entryId = {}", entryId);

        LedgerEntry entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> {
                    log.warn("[장부 내역 삭제] 존재하지 않는 내역입니다. entryId = {}", entryId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        if (entry.isAutoEntry() && entry.getSchedule() != null) {
            log.warn("[장부 내역 삭제] 원본 일정이 살아있는 자동 기록은 삭제할 수 없습니다. entryId = {}", entryId);
            throw new CustomException(BudgetErrorCode.LEDGER_409_AUTO_ENTRY_LOCKED);
        }

        ledgerEntryRepository.delete(entry);

        log.info("[장부 내역 삭제] 삭제를 완료했습니다. entryId = {}", entryId);
    }
}
