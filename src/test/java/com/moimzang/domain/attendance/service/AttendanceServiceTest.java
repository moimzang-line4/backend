package com.moimzang.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.mapper.AttendanceMapper;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private ScheduleAttendeeRepository scheduleAttendeeRepository;

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private RoundAttendeeRepository roundAttendeeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final AttendanceMapper attendanceMapper = Mappers.getMapper(AttendanceMapper.class);

    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(
                scheduleAttendeeRepository, scheduleRoundRepository, roundAttendeeRepository,
                scheduleRepository, attendanceMapper);
    }

    private ScheduleRound roundWithId(Long id, Schedule schedule, Integer roundNo, String label) {
        ScheduleRound round = ScheduleRound.create(schedule, roundNo, label);
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    @Test
    void listRounds_정상_차수_목록을_hasReceipt와_함께_roundNo_순으로_반환한다() {
        Schedule schedule = mock(Schedule.class);
        ScheduleRound round1 = roundWithId(1L, schedule, 1, "1차");
        ScheduleRound round2 = roundWithId(2L, schedule, 2, "2차");
        ReflectionTestUtils.setField(round2, "totalAmount", 30000);

        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of(round1, round2));

        ScheduleRoundListResponse response = attendanceService.listRounds(10L);

        assertThat(response.getRounds()).hasSize(2);
        assertThat(response.getRounds().get(0).getRoundId()).isEqualTo(1L);
        assertThat(response.getRounds().get(0).getHasReceipt()).isFalse();
        assertThat(response.getRounds().get(1).getRoundId()).isEqualTo(2L);
        assertThat(response.getRounds().get(1).getHasReceipt()).isTrue();
    }

    @Test
    void listRounds_라운드가_없으면_빈_목록을_반환한다() {
        Schedule schedule = mock(Schedule.class);

        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(10L)).willReturn(List.of());

        ScheduleRoundListResponse response = attendanceService.listRounds(10L);

        assertThat(response.getRounds()).isEmpty();
    }

    @Test
    void listRounds_일정이_없으면_COMMON_404를_던진다() {
        given(scheduleRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.listRounds(10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void createRound_정상_마지막_차수_1_증가하고_기존_참가자_전원을_미출석으로_포함한다() {
        Schedule schedule = mock(Schedule.class);
        ScheduleRound lastRound = roundWithId(5L, schedule, 2, "2차");
        ScheduleAttendee attendee1 = mock(ScheduleAttendee.class);
        ScheduleAttendee attendee2 = mock(ScheduleAttendee.class);

        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findLastRounds(10L, PageRequest.of(0, 1))).willReturn(List.of(lastRound));
        given(scheduleRoundRepository.save(any(ScheduleRound.class))).willAnswer(invocation -> {
            ScheduleRound saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        given(scheduleAttendeeRepository.findByScheduleId(10L)).willReturn(List.of(attendee1, attendee2));

        ScheduleRoundCreateResponse response = attendanceService.createRound(10L);

        assertThat(response.getRoundId()).isEqualTo(100L);
        assertThat(response.getRoundNo()).isEqualTo(3);
        assertThat(response.getLabel()).isEqualTo("3차");

        ArgumentCaptor<RoundAttendee> captor = ArgumentCaptor.forClass(RoundAttendee.class);
        verify(roundAttendeeRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RoundAttendee::getScheduleAttendee)
                .containsExactly(attendee1, attendee2);
        assertThat(captor.getAllValues()).allSatisfy(ra -> {
            assertThat(ra.getIsCheckedIn()).isFalse();
            assertThat(ra.getScheduleRound().getId()).isEqualTo(100L);
        });
    }

    @Test
    void createRound_기존_차수가_없으면_roundNo_1부터_시작한다() {
        Schedule schedule = mock(Schedule.class);

        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findLastRounds(10L, PageRequest.of(0, 1))).willReturn(List.of());
        given(scheduleRoundRepository.save(any(ScheduleRound.class))).willAnswer(invocation -> {
            ScheduleRound saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        given(scheduleAttendeeRepository.findByScheduleId(10L)).willReturn(List.of());

        ScheduleRoundCreateResponse response = attendanceService.createRound(10L);

        assertThat(response.getRoundNo()).isEqualTo(1);
        assertThat(response.getLabel()).isEqualTo("1차");
        verify(roundAttendeeRepository, never()).save(any());
    }

    @Test
    void createRound_일정이_없으면_COMMON_404를_던진다() {
        given(scheduleRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.createRound(10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
