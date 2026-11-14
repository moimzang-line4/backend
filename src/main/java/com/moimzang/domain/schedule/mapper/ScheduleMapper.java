package com.moimzang.domain.schedule.mapper;

import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleSummaryResponse;
import com.moimzang.domain.schedule.dto.response.UpcomingScheduleResponse;
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

    // 캘린더 일정 1건을 변환한다
    @Mapping(target = "scheduleId", source = "id")
    @Mapping(target = "categoryName", source = "scheduleCategory.name")
    ScheduleSummaryResponse toSummaryResponse(Schedule schedule);

    // 캘린더 일정 목록을 변환한다
    List<ScheduleSummaryResponse> toSummaryResponses(List<Schedule> schedules);

    // 다가오는 운영 일정 1건을 변환한다. 운영 공간인 일정은 메뉴가 항상 4개(메모장·출석·정산·자료)다
    @Mapping(target = "scheduleId", source = "id")
    @Mapping(target = "categoryName", source = "scheduleCategory.name")
    @Mapping(target = "operationMenuCount", constant = "4")
    UpcomingScheduleResponse toUpcomingResponse(Schedule schedule);

    // 다가오는 운영 일정 목록을 변환한다
    List<UpcomingScheduleResponse> toUpcomingResponses(List<Schedule> schedules);
}
