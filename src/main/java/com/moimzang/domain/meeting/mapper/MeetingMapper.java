package com.moimzang.domain.meeting.mapper;

import com.moimzang.domain.meeting.dto.response.MeetingAgendaDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingProgressResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MeetingMapper {

    // 목록 카드 하나를 변환한다. agendaCount는 별도 배치 집계값이라 파라미터로 받는다
    @Mapping(target = "meetingId", source = "meeting.id")
    @Mapping(target = "categoryName", source = "meeting.meetingCategory.name")
    MeetingSummaryResponse toSummaryResponse(Meeting meeting, Long agendaCount);

    // 안건 1건을 상세 응답으로 변환한다
    @Mapping(target = "agendaId", source = "id")
    MeetingAgendaDetailResponse toAgendaResponse(MeetingAgenda agenda);

    // 안건 목록을 상세 응답 목록으로 변환한다
    List<MeetingAgendaDetailResponse> toAgendaResponses(List<MeetingAgenda> agendas);

    // 회의 상세를 변환한다. agendas·progress는 Service가 별도 조회·계산해 넘긴 값이다
    @Mapping(target = "meetingId", source = "meeting.id")
    @Mapping(target = "categoryId", source = "meeting.meetingCategory.id")
    MeetingDetailResponse toDetailResponse(Meeting meeting, List<MeetingAgenda> agendas, MeetingProgressResponse progress);
}
