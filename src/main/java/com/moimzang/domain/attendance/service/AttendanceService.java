package com.moimzang.domain.attendance.service;

import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundSummaryResponse;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.mapper.AttendanceMapper;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
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
}
