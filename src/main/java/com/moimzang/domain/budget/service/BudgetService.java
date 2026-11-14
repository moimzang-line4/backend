package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.dto.request.LedgerEntryCreateRequest;
import com.moimzang.domain.budget.dto.request.LedgerEntryUpdateRequest;
import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryOriginResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntrySummaryResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.dto.response.LedgerFileResponse;
import com.moimzang.domain.budget.dto.response.LedgerSummaryResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.exception.BudgetErrorCode;
import com.moimzang.domain.budget.mapper.BudgetMapper;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
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
    private final FileRepository fileRepository;
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

        LedgerSource source = resolveSource(entry);
        LedgerEntryOriginResponse origin = entry.isAutoEntry() && entry.getSchedule() != null
                ? budgetMapper.toOriginResponse(entry.getSchedule())
                : null;

        log.info("[장부 내역 상세 조회] 조회를 완료했습니다. entryId = {}, source = {}", entryId, source);
        return budgetMapper.toDetailResponse(entry, source, origin);
    }

    // 예산 요약·내역 조회. currentBalance·totalIncome·totalExpense는 그룹 전체(모든 기수) 누적이고
    // (사용자 결정, currentBalance=totalIncome-totalExpense), entries는 이 기수 내역만 cursor
    // 페이지네이션으로 조회한다(정렬: entryDate desc, id desc)
    public LedgerSummaryResponse getLedgerSummary(Long termId, String type, String cursor, Integer size) {
        log.info("[예산 요약·내역 조회] 조회를 시작합니다. termId = {}, type = {}, cursor = {}", termId, type, cursor);

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[예산 요약·내역 조회] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        LedgerType typeFilter = parseType(type);
        int limit = CursorPageConstants.resolveSize(size);

        LocalDate cursorEntryDate = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorEntryDate = LocalDate.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[예산 요약·내역 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<LedgerEntry> fetched = ledgerEntryRepository.search(termId, typeFilter, cursorEntryDate, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<LedgerEntry> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<LedgerEntrySummaryResponse> entries = page.stream()
                .map(entry -> budgetMapper.toSummaryItem(entry, resolveSource(entry)))
                .toList();
        String nextCursor = hasNext ? buildCursor(page.get(page.size() - 1)) : null;

        List<LedgerTypeTotal> groupTotals = ledgerEntryRepository.aggregateByTypeForGroup(groupTerm.getGroup().getId());
        long totalIncome = totalFor(groupTotals, LedgerType.INCOME);
        long totalExpense = totalFor(groupTotals, LedgerType.EXPENSE);

        LedgerFileResponse ledgerFile = fileRepository.findActiveFile(termId, FileCategory.LEDGER_FILE)
                .map(budgetMapper::toLedgerFileResponse)
                .orElse(null);

        log.info("[예산 요약·내역 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}", termId, entries.size(), hasNext);
        return LedgerSummaryResponse.builder()
                .ledgerFile(ledgerFile)
                .currentBalance(totalIncome - totalExpense)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .entries(entries)
                .nextCursor(nextCursor)
                .build();
    }

    private LedgerType parseType(String type) {
        if (type == null || "ALL".equals(type)) {
            return null;
        }
        try {
            return LedgerType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("[예산 요약·내역 조회] type 값이 올바르지 않습니다. type = {}", type);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }
    }

    private long totalFor(List<LedgerTypeTotal> totals, LedgerType type) {
        return totals.stream()
                .filter(total -> total.type() == type)
                .mapToLong(LedgerTypeTotal::totalAmount)
                .findFirst()
                .orElse(0L);
    }

    private String buildCursor(LedgerEntry last) {
        return CursorCodec.encode(last.getEntryDate().toString(), last.getId());
    }

    // 자동 기록 여부로 source를 판별한다(decisions #65)
    private LedgerSource resolveSource(LedgerEntry entry) {
        return entry.isAutoEntry() ? LedgerSource.RECEIPT : LedgerSource.MANUAL;
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
