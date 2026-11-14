package com.moimzang.domain.schedule.mapper;

import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
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
}
