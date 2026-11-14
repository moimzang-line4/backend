package com.moimzang.domain.budget.service;

import com.moimzang.domain.budget.dto.response.LedgerExportResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.repository.LedgerTypeTotal;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 장부 엑셀 다운로드(API-075). 원본 업로드 엑셀(API-063 LEDGER_FILE)과 무관하게, 서비스가
// 가진 ledger_entry로 새 엑셀을 매번 새로 만든다 — File 엔티티·문서함에 등록하지 않는다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerExportService {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GroupTermRepository groupTermRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerExportExcelGenerator excelGenerator;
    private final LedgerExportKeyGenerator keyGenerator;
    private final FileStorage fileStorage;

    public LedgerExportResponse export(Long termId) {
        log.info("[장부 엑셀 다운로드] 생성을 시작합니다. termId = {}", termId);

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[장부 엑셀 다운로드] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        // 행은 이 기수 내역만(API-069의 entries와 같은 범위), 요약은 그룹 전체 누적(API-069의
        // currentBalance·totalIncome·totalExpense와 같은 집계, decisions #68)
        List<LedgerEntry> entries = ledgerEntryRepository.findAllForExport(termId);
        List<LedgerTypeTotal> groupTotals = ledgerEntryRepository.aggregateByTypeForGroup(groupTerm.getGroup().getId());
        long totalIncome = totalFor(groupTotals, LedgerType.INCOME);
        long totalExpense = totalFor(groupTotals, LedgerType.EXPENSE);

        byte[] excelBytes = excelGenerator.generate(totalIncome, totalExpense, totalIncome - totalExpense, entries);

        String key = keyGenerator.generate();
        fileStorage.store(key, new ByteArrayInputStream(excelBytes), excelBytes.length, XLSX_CONTENT_TYPE);

        String fileName = buildFileName(groupTerm);
        LocalDateTime generatedAt = LocalDateTime.now();

        log.info("[장부 엑셀 다운로드] 생성을 완료했습니다. termId = {}, key = {}, entryCount = {}", termId, key, entries.size());
        return LedgerExportResponse.builder()
                .downloadUrl(fileStorage.toUrl(key))
                .fileName(fileName)
                .generatedAt(generatedAt)
                .build();
    }

    private long totalFor(List<LedgerTypeTotal> totals, LedgerType type) {
        return totals.stream()
                .filter(total -> total.type() == type)
                .mapToLong(LedgerTypeTotal::totalAmount)
                .findFirst()
                .orElse(0L);
    }

    private String buildFileName(GroupTerm groupTerm) {
        String today = LocalDate.now().format(FILE_NAME_DATE);
        return "%s_%s_장부_%s.xlsx".formatted(groupTerm.getGroup().getName(), groupTerm.getName(), today);
    }
}
