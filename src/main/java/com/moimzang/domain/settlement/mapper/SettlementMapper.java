package com.moimzang.domain.settlement.mapper;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.settlement.dto.response.AccountResponse;
import com.moimzang.domain.settlement.dto.response.PaymentStatusResponse;
import com.moimzang.domain.settlement.dto.response.PaymentSummaryResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementImportAttendeeResponse;
import com.moimzang.domain.settlement.dto.response.SettlementHomeReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeAccountResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeRoundResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetSaveResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.repository.PaymentSummary;
import java.time.LocalDateTime;
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

    // 정산 명단 조회(API-037)의 대상 한 명을 변환한다
    @Mapping(target = "settlementTargetId", source = "id")
    @Mapping(target = "attendeeId", source = "scheduleAttendee.id")
    @Mapping(target = "name", source = "scheduleAttendee.name")
    @Mapping(target = "affiliation", source = "scheduleAttendee.affiliation")
    @Mapping(target = "phoneLast4", source = "scheduleAttendee.phoneLast4")
    SettlementTargetResponse toSettlementTargetResponse(SettlementTarget target);

    // 정산 명단 저장(API-038) 응답으로 변환한다. targetCount·billedAmount는 Service가 계산해 넘기는 값이다
    @Mapping(target = "label", source = "round.label")
    SettlementTargetSaveResponse toSettlementTargetSaveResponse(ScheduleRound round, long targetCount, int billedAmount);

    // 행사 명단 불러오기(API-039)의 참가자 한 명을 변환한다
    @Mapping(target = "attendeeId", source = "scheduleAttendee.id")
    @Mapping(target = "name", source = "scheduleAttendee.name")
    @Mapping(target = "affiliation", source = "scheduleAttendee.affiliation")
    @Mapping(target = "phoneLast4", source = "scheduleAttendee.phoneLast4")
    SettlementImportAttendeeResponse toImportAttendeeResponse(RoundAttendee roundAttendee);

    // 입금 확인·되돌리기(API-040) 응답으로 변환한다. paymentStatus·paidAt은 Service가
    // 그 사람의 전 차수를 반영해 계산해 넘기는 값이다
    @Mapping(target = "attendeeId", source = "attendee.id")
    @Mapping(target = "name", source = "attendee.name")
    PaymentStatusResponse toPaymentStatusResponse(ScheduleAttendee attendee, PaymentStatus paymentStatus, LocalDateTime paidAt);

    // 입금 현황 조회(API-042)의 한 명을 변환한다
    @Mapping(target = "attendeeId", source = "attendee.id")
    @Mapping(target = "name", source = "attendee.name")
    @Mapping(target = "affiliation", source = "attendee.affiliation")
    @Mapping(target = "phoneLast4", source = "attendee.phoneLast4")
    PaymentSummaryResponse toPaymentSummaryResponse(PaymentSummary summary);

    // 입금 안내 계좌 설정(API-043) 응답으로 변환한다
    @Mapping(target = "roundId", source = "id")
    AccountResponse toAccountResponse(ScheduleRound round);

    // 정산 공지 생성(API-044)의 대표 계좌로 변환한다(roundId 없이 계좌 정보만)
    SettlementNoticeAccountResponse toNoticeAccountResponse(ScheduleRound round);

    // 정산 공지 생성(API-044)의 차수 한 줄을 변환한다. billedAmount는 Service가 계산해 넘기는 값이다
    @Mapping(target = "label", source = "round.label")
    SettlementNoticeRoundResponse toNoticeRoundResponse(ScheduleRound round, int billedAmount);

    // 정산 홈 조회(API-045)의 영수증 등록 차수 한 줄을 변환한다. settlementStatus는 Service가
    // 계산해 넘기는 값이고, registeredAt은 전용 컬럼이 없어 round.updatedAt으로 대체한다
    @Mapping(target = "roundId", source = "round.id")
    @Mapping(target = "registeredAt", source = "round.updatedAt")
    SettlementHomeReceiptResponse toHomeReceiptResponse(ScheduleRound round, String settlementStatus);
}
