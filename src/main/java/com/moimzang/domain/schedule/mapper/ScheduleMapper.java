package com.moimzang.domain.schedule.mapper;

import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ScheduleMapper {

    // 일정 카테고리 1건을 변환한다
    @Mapping(target = "categoryId", source = "id")
    ScheduleCategoryResponse toCategoryResponse(ScheduleCategory category);

    // 일정 카테고리 목록을 변환한다
    List<ScheduleCategoryResponse> toCategoryResponses(List<ScheduleCategory> categories);

    // 일정 상세를 변환한다. dDay는 오늘 날짜에 따라 달라져서 Service가 계산해 넘긴다
    @Mapping(target = "scheduleId", source = "schedule.id")
    @Mapping(target = "categoryId", source = "schedule.scheduleCategory.id")
    @Mapping(target = "categoryName", source = "schedule.scheduleCategory.name")
    ScheduleDetailResponse toDetailResponse(Schedule schedule, Long dDay);
}
