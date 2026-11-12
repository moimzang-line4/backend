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
import com.moimzang.domain.attendance.dto.response.RoundAttendanceListResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundCreateResponse;
import com.moimzang.domain.attendance.dto.response.ScheduleRoundListResponse;
import com.moimzang.domain.attendance.enums.AttendanceStatusFilter;
import com.moimzang.domain.attendance.mapper.AttendanceMapper;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
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

    private ScheduleAttendee attendeeWithId(Long id, String name, String affiliation, String phoneLast4) {
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "id", id);
        ReflectionTestUtils.setField(attendee, "name", name);
        ReflectionTestUtils.setField(attendee, "affiliation", affiliation);
        ReflectionTestUtils.setField(attendee, "phoneLast4", phoneLast4);
        return attendee;
    }

    private RoundAttendee roundAttendeeWithId(Long id, ScheduleRound round, ScheduleAttendee attendee, LocalDateTime createdAt) {
        RoundAttendee roundAttendee = RoundAttendee.create(round, attendee);
        ReflectionTestUtils.setField(roundAttendee, "id", id);
        ReflectionTestUtils.setField(roundAttendee, "createdAt", createdAt);
        return roundAttendee;
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

    @Test
    void getRoundAttendees_정상_scheduleTitle과_전체_기준_progress와_참가자_목록을_반환한다() {
        Schedule schedule = mock(Schedule.class);
        given(schedule.getTitle()).willReturn("정기 모임");
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");

        ScheduleAttendee attendee1 = attendeeWithId(100L, "김모임", "컴퓨터공학과", "1234");
        ScheduleAttendee attendee2 = attendeeWithId(101L, "박모임", "경영학과", "5678");
        RoundAttendee ra1 = roundAttendeeWithId(1L, round, attendee1, LocalDateTime.now());
        RoundAttendee ra2 = roundAttendeeWithId(2L, round, attendee2, LocalDateTime.now());

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.search(1L, false, null, null, null, 21)).willReturn(List.of(ra1, ra2));
        given(roundAttendeeRepository.countByScheduleRoundId(1L)).willReturn(5L);
        given(roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(1L, true)).willReturn(3L);

        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(1L, null, null, null, null);

        assertThat(response.getScheduleTitle()).isEqualTo("정기 모임");
        assertThat(response.getProgress().getCheckedCount()).isEqualTo(3L);
        assertThat(response.getProgress().getTotalCount()).isEqualTo(5L);
        assertThat(response.getProgress().getRate()).isEqualTo(60);
        assertThat(response.getAttendees()).hasSize(2);
        assertThat(response.getAttendees().get(0).getAttendeeId()).isEqualTo(100L);
        assertThat(response.getAttendees().get(0).getName()).isEqualTo("김모임");
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void getRoundAttendees_status가_ALL이면_isCheckedIn_필터없이_조회한다() {
        Schedule schedule = mock(Schedule.class);
        given(schedule.getTitle()).willReturn("정기 모임");
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.search(1L, null, null, null, null, 21)).willReturn(List.of());
        given(roundAttendeeRepository.countByScheduleRoundId(1L)).willReturn(0L);
        given(roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(1L, true)).willReturn(0L);

        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(1L, AttendanceStatusFilter.ALL, null, null, null);

        assertThat(response.getProgress().getRate()).isEqualTo(0);
        verify(roundAttendeeRepository).search(1L, null, null, null, null, 21);
    }

    @Test
    void getRoundAttendees_size보다_1건_더_가져와서_다음_페이지가_있으면_nextCursor를_만든다() {
        Schedule schedule = mock(Schedule.class);
        given(schedule.getTitle()).willReturn("정기 모임");
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 19, 0, 0);
        ScheduleAttendee attendee1 = attendeeWithId(100L, "김모임", null, null);
        ScheduleAttendee attendee2 = attendeeWithId(101L, "박모임", null, null);
        RoundAttendee ra1 = roundAttendeeWithId(1L, round, attendee1, createdAt);
        RoundAttendee ra2 = roundAttendeeWithId(2L, round, attendee2, createdAt);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.search(1L, false, null, null, null, 2)).willReturn(List.of(ra1, ra2));
        given(roundAttendeeRepository.countByScheduleRoundId(1L)).willReturn(2L);
        given(roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(1L, true)).willReturn(0L);

        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(1L, null, null, null, 1);

        assertThat(response.getAttendees()).hasSize(1);
        assertThat(response.getAttendees().get(0).getAttendeeId()).isEqualTo(100L);
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void getRoundAttendees_차수가_없으면_COMMON_404를_던진다() {
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.getRoundAttendees(1L, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getRoundAttendees_cursor_형식이_잘못되면_COMMON_400을_던진다() {
        Schedule schedule = mock(Schedule.class);
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> attendanceService.getRoundAttendees(1L, null, null, "잘못된cursor값", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getRoundAttendees_progress는_status_필터와_무관하게_항상_차수_전체_기준으로_계산한다() {
        Schedule schedule = mock(Schedule.class);
        given(schedule.getTitle()).willReturn("정기 모임");
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.search(1L, true, null, null, null, 21)).willReturn(List.of());
        given(roundAttendeeRepository.countByScheduleRoundId(1L)).willReturn(10L);
        given(roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(1L, true)).willReturn(4L);

        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(1L, AttendanceStatusFilter.CHECKED, null, null, null);

        assertThat(response.getProgress().getTotalCount()).isEqualTo(10L);
        assertThat(response.getProgress().getCheckedCount()).isEqualTo(4L);
        verify(roundAttendeeRepository).countByScheduleRoundId(1L);
        verify(roundAttendeeRepository).countByScheduleRoundIdAndIsCheckedIn(1L, true);
    }

    @Test
    void getRoundAttendees_참가자가_0명이면_0으로_나누지_않고_rate_0을_반환한다() {
        Schedule schedule = mock(Schedule.class);
        given(schedule.getTitle()).willReturn("정기 모임");
        ScheduleRound round = roundWithId(1L, schedule, 1, "1차");

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.search(1L, false, null, null, null, 21)).willReturn(List.of());
        given(roundAttendeeRepository.countByScheduleRoundId(1L)).willReturn(0L);
        given(roundAttendeeRepository.countByScheduleRoundIdAndIsCheckedIn(1L, true)).willReturn(0L);

        RoundAttendanceListResponse response = attendanceService.getRoundAttendees(1L, null, null, null, null);

        assertThat(response.getProgress().getTotalCount()).isEqualTo(0L);
        assertThat(response.getProgress().getCheckedCount()).isEqualTo(0L);
        assertThat(response.getProgress().getRate()).isEqualTo(0);
        assertThat(response.getAttendees()).isEmpty();
    }
}
