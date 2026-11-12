package com.moimzang.domain.attendance.service;

import com.moimzang.domain.attendance.dto.request.OnSiteAttendeeAddRequest;
import com.moimzang.domain.attendance.dto.response.OnSiteAttendeeAddResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendanceListResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendanceProgressResponse;
import com.moimzang.domain.attendance.dto.response.RoundAttendeeResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundSummaryResponse;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.enums.AttendanceStatusFilter;
import com.moimzang.domain.attendance.exception.AttendanceErrorCode;
import com.moimzang.domain.attendance.mapper.AttendanceMapper;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final RoundAttendeeRepository roundAttendeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final AttendanceMapper attendanceMapper;

    // 일정의 차수 목록을 roundNo 오름차순으로 조회한다
    public ScheduleRoundListResponse listRounds(Long scheduleId) {
        log.info("[차수 목록 조회] 조회를 시작합니다. scheduleId = {}", scheduleId);

        scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[차수 목록 조회] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        List<ScheduleRound> rounds = scheduleRoundRepository.findOrderedRounds(scheduleId);
        List<ScheduleRoundSummaryResponse> summaries = rounds.stream()
                .map(round -> attendanceMapper.toRoundSummaryResponse(round, round.hasReceipt()))
                .toList();

        log.info("[차수 목록 조회] 조회를 완료했습니다. scheduleId = {}, count = {}", scheduleId, summaries.size());
        return ScheduleRoundListResponse.builder()
                .rounds(summaries)
                .build();
    }

    // 일정에 새 차수를 추가한다. roundNo는 마지막+1(없으면 1), label은 "{roundNo}차"로 자동
    // 생성하고, 그 일정의 기존 참가자 전원을 이 차수에 미출석 상태로 함께 넣는다(팀 확인 전까지 유지,
    // 바뀌면 재논의)
    @Transactional
    public ScheduleRoundCreateResponse createRound(Long scheduleId) {
        log.info("[차수 추가] 추가를 시작합니다. scheduleId = {}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[차수 추가] 존재하지 않는 일정입니다. scheduleId = {}", scheduleId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        int nextRoundNo = scheduleRoundRepository.findLastRounds(scheduleId, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(last -> last.getRoundNo() + 1)
                .orElse(1);
        String label = nextRoundNo + "차";

        ScheduleRound round = scheduleRoundRepository.save(ScheduleRound.create(schedule, nextRoundNo, label));

        List<ScheduleAttendee> attendees = scheduleAttendeeRepository.findByScheduleId(scheduleId);
        attendees.forEach(attendee -> roundAttendeeRepository.save(RoundAttendee.create(round, attendee)));

        log.info("[차수 추가] 추가를 완료했습니다. roundId = {}, roundNo = {}, attendeeCount = {}",
                round.getId(), nextRoundNo, attendees.size());
        return attendanceMapper.toRoundCreateResponse(round);
    }

    // 차수의 출석 현황을 조회한다. status 기본값은 UNCHECKED, progress는 필터와 무관하게 그
    // 차수 전체 참가자 기준으로 매번 계산한다(페이지네이션용 totalCount가 아니다)
    public RoundAttendanceListResponse getRoundAttendees(Long roundId, AttendanceStatusFilter status,
                                                           String keyword, String cursor, Integer size) {
        log.info("[차수 출석 현황 조회] 조회를 시작합니다. roundId = {}, status = {}, keyword = {}, cursor = {}",
                roundId, status, keyword, cursor);

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[차수 출석 현황 조회] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        AttendanceStatusFilter resolvedStatus = status == null ? AttendanceStatusFilter.UNCHECKED : status;
        int limit = CursorPageConstants.resolveSize(size);

        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorId = decoded.getId();
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(decoded.getSortValue()))
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (RuntimeException e) {
                log.warn("[차수 출석 현황 조회] cursor 형식이 올바르지 않습니다. roundId = {}, cursor = {}", roundId, cursor);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<RoundAttendee> fetched = roundAttendeeRepository.search(
                roundId, resolvedStatus.toIsCheckedInOrNull(), keyword, cursorCreatedAt, cursorId, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<RoundAttendee> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<RoundAttendeeResponse> attendees = page.stream()
                .map(attendanceMapper::toAttendeeResponse)
                .toList();
        String nextCursor = hasNext ? buildCursor(page.get(page.size() - 1)) : null;

        long totalCount = roundAttendeeRepository.countByScheduleRoundId(roundId);
        long checkedCount = roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(roundId, true);
        int rate = totalCount == 0 ? 0 : (int) Math.round(checkedCount * 100.0 / totalCount);

        RoundAttendanceProgressResponse progress = RoundAttendanceProgressResponse.builder()
                .checkedCount(checkedCount)
                .totalCount(totalCount)
                .rate(rate)
                .build();

        log.info("[차수 출석 현황 조회] 조회를 완료했습니다. roundId = {}, count = {}, hasNext = {}", roundId, attendees.size(), hasNext);
        return RoundAttendanceListResponse.builder()
                .scheduleTitle(round.getSchedule().getTitle())
                .progress(progress)
                .attendees(attendees)
                .nextCursor(nextCursor)
                .build();
    }

    private String buildCursor(RoundAttendee last) {
        String sortValue = String.valueOf(last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return CursorCodec.encode(sortValue, last.getId());
    }

    // 그 차수에 현장 참가자를 미출석 상태로 추가한다. 같은 일정에 이름이 같은 참가자가 있으면
    // 거부하고, allowSameName=true면 새 참가자로 추가한다(이름은 trim해서 비교·저장)
    @Transactional
    public OnSiteAttendeeAddResponse addOnSiteAttendee(Long roundId, OnSiteAttendeeAddRequest request) {
        log.info("[현장 인원 추가] 추가를 시작합니다. roundId = {}, allowSameName = {}", roundId, request.isAllowSameName());

        ScheduleRound round = scheduleRoundRepository.findById(roundId)
                .orElseThrow(() -> {
                    log.warn("[현장 인원 추가] 존재하지 않는 차수입니다. roundId = {}", roundId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        String name = request.getName().trim();
        Long scheduleId = round.getSchedule().getId();
        if (!request.isAllowSameName() && scheduleAttendeeRepository.existsByScheduleIdAndName(scheduleId, name)) {
            log.warn("[현장 인원 추가] 같은 이름의 참가자가 이미 있습니다. scheduleId = {}, name = {}", scheduleId, name);
            throw new CustomException(AttendanceErrorCode.ATTENDANCE_409_DUPLICATE_NAME);
        }

        ScheduleAttendee attendee = scheduleAttendeeRepository.save(
                ScheduleAttendee.createOnSite(round.getSchedule(), name, request.getAffiliation(), request.getPhoneLast4()));
        roundAttendeeRepository.save(RoundAttendee.create(round, attendee));

        log.info("[현장 인원 추가] 추가를 완료했습니다. roundId = {}, attendeeId = {}", roundId, attendee.getId());
        return attendanceMapper.toOnSiteAttendeeAddResponse(attendee, false);
    }

    // 그 차수에서 참가자를 제외한다(이 차수의 출석 기록만 삭제, 다른 차수·참가자 원본은 유지)
    @Transactional
    public void removeAttendee(Long roundId, Long attendeeId) {
        log.info("[참가자 제외] 제외를 시작합니다. roundId = {}, attendeeId = {}", roundId, attendeeId);

        RoundAttendee roundAttendee = roundAttendeeRepository.findByScheduleRoundIdAndScheduleAttendeeId(roundId, attendeeId)
                .orElseThrow(() -> {
                    log.warn("[참가자 제외] 그 차수에 없는 참가자입니다. roundId = {}, attendeeId = {}", roundId, attendeeId);
                    return new CustomException(CommonErrorCode.NOT_FOUND);
                });

        roundAttendeeRepository.delete(roundAttendee);
        log.info("[참가자 제외] 제외를 완료했습니다. roundId = {}, attendeeId = {}", roundId, attendeeId);
    }
}
