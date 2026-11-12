package com.moimzang.domain.attendance.mapper;

import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundSummaryResponse;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AttendanceMapper {

    // 차수 목록 카드 하나를 변환한다. hasReceipt는 Service가 계산해 넘긴 값이다
    @Mapping(target = "roundId", source = "round.id")
    ScheduleRoundSummaryResponse toRoundSummaryResponse(ScheduleRound round, boolean hasReceipt);

    // 차수 생성 응답으로 변환한다
    @Mapping(target = "roundId", source = "id")
    ScheduleRoundCreateResponse toRoundCreateResponse(ScheduleRound round);
}
