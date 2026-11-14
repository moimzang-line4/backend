package com.moimzang.domain.budget.mapper;

import com.moimzang.domain.budget.dto.response.LedgerEntryCreateResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryDetailResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryOriginResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntrySummaryResponse;
import com.moimzang.domain.budget.dto.response.LedgerEntryUpdateResponse;
import com.moimzang.domain.budget.dto.response.LedgerFileResponse;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerSource;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.schedule.entity.Schedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BudgetMapper {

    // 장부 직접 입력(API-070) 응답으로 변환한다
    @Mapping(target = "entryId", source = "id")
    LedgerEntryCreateResponse toCreateResponse(LedgerEntry entry);

    // 장부 내역 상세 조회(API-071) 응답으로 변환한다. source·origin은 Service가 판별해 넘기는 값이다
    @Mapping(target = "entryId", source = "entry.id")
    LedgerEntryDetailResponse toDetailResponse(LedgerEntry entry, LedgerSource source, LedgerEntryOriginResponse origin);

    // API-071 origin(자동 기록 출처 일정)으로 변환한다
    @Mapping(target = "scheduleId", source = "id")
    LedgerEntryOriginResponse toOriginResponse(Schedule schedule);

    // 장부 내역 수정(API-072) 응답으로 변환한다
    @Mapping(target = "entryId", source = "id")
    LedgerEntryUpdateResponse toUpdateResponse(LedgerEntry entry);

    // 예산 요약·내역 조회(API-069) 목록의 한 줄로 변환한다. source는 Service가 판별해 넘기는 값이다
    @Mapping(target = "entryId", source = "entry.id")
    LedgerEntrySummaryResponse toSummaryItem(LedgerEntry entry, LedgerSource source);

    // API-069 ledgerFile로 변환한다
    @Mapping(target = "fileId", source = "id")
    LedgerFileResponse toLedgerFileResponse(File file);
}
