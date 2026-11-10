package com.moimzang.domain.meeting.mapper;

import com.moimzang.domain.meeting.dto.response.MeetingSummaryResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MeetingMapper {

    @Mapping(target = "meetingId", source = "meeting.id")
    @Mapping(target = "categoryName", source = "meeting.meetingCategory.name")
    MeetingSummaryResponse toSummaryResponse(Meeting meeting, Long agendaCount);
}
