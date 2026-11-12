package com.moimzang.domain.attendance.mapper;

import com.moimzang.domain.attendance.dto.response.OnSiteAttendeeAddResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendanceProgressResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendeeCheckInResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendeeResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundSummaryResponse;
import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
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

    // 차수 출석 현황의 참가자 한 명을 변환한다. attendeeId는 round_attendee가 아니라
    // scheduleAttendee(참가자 원본)의 id다 — API-031/032가 그 id로 참가자를 식별한다
    @Mapping(target = "attendeeId", source = "scheduleAttendee.id")
    @Mapping(target = "name", source = "scheduleAttendee.name")
    @Mapping(target = "affiliation", source = "scheduleAttendee.affiliation")
    @Mapping(target = "phoneLast4", source = "scheduleAttendee.phoneLast4")
    RoundAttendeeResponse toAttendeeResponse(RoundAttendee roundAttendee);

    // 현장 인원 추가(API-030) 응답으로 변환한다. isCheckedIn은 Service가 넘기는 값이다(추가 직후는 항상 false)
    @Mapping(target = "attendeeId", source = "attendee.id")
    @Mapping(target = "name", source = "attendee.name")
    @Mapping(target = "affiliation", source = "attendee.affiliation")
    @Mapping(target = "phoneLast4", source = "attendee.phoneLast4")
    OnSiteAttendeeAddResponse toOnSiteAttendeeAddResponse(ScheduleAttendee attendee, Boolean isCheckedIn);

    // 출석 처리·되돌리기(API-031) 응답으로 변환한다. progress는 Service가 재계산해 넘기는 값이다
    @Mapping(target = "attendeeId", source = "roundAttendee.scheduleAttendee.id")
    @Mapping(target = "name", source = "roundAttendee.scheduleAttendee.name")
    @Mapping(target = "isCheckedIn", source = "roundAttendee.isCheckedIn")
    @Mapping(target = "checkedInAt", source = "roundAttendee.checkedInAt")
    RoundAttendeeCheckInResponse toCheckInResponse(RoundAttendee roundAttendee, RoundAttendanceProgressResponse progress);
}
