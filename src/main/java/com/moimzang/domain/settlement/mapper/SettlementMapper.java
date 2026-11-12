package com.moimzang.domain.settlement.mapper;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SettlementMapper {

    // 영수증 조회(API-033) 응답으로 변환한다. isLocked는 Service가 계산해 넘기는 값이고,
    // registeredAt은 전용 컬럼이 없어 round.updatedAt으로 대체한다
    @Mapping(target = "roundId", source = "round.id")
    @Mapping(target = "registeredAt", source = "round.updatedAt")
    ReceiptResponse toReceiptResponse(ScheduleRound round, Boolean isLocked);

    // 영수증 등록·수정(API-034) 응답으로 변환한다
    @Mapping(target = "roundId", source = "id")
    ReceiptRegisterResponse toReceiptRegisterResponse(ScheduleRound round);
}
