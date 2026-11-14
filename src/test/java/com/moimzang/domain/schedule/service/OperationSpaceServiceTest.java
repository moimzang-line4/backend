package com.moimzang.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.schedule.dto.request.AgendaLinkCreateRequest;
import com.moimzang.domain.schedule.dto.response.AgendaLinkCreateResponse;
import com.moimzang.domain.schedule.dto.response.LinkedAgendaListResponse;
import com.moimzang.domain.schedule.dto.response.LinkedAgendaResponse;
import com.moimzang.domain.schedule.dto.response.OperationSpaceListResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.moimzang.domain.schedule.enums.OperationSpaceSort;
import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceType;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.repository.OperationSpaceCursor;
import com.moimzang.domain.schedule.repository.OperationSpaceQueryRepository;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OperationSpaceServiceTest {

    @Mock
    private OperationSpaceQueryRepository operationSpaceQueryRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;

    @Mock
    private MeetingAgendaRepository meetingAgendaRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    private OperationSpaceService operationSpaceService;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        operationSpaceService = new OperationSpaceService(operationSpaceQueryRepository, scheduleRepository,
                scheduleAgendaLinkRepository, meetingAgendaRepository, groupTermRepository);
        groupTerm = mock(GroupTerm.class);
    }

    @Test
    void listOperationSpaces_일정과_회의록을_날짜_내림차순으로_합치고_다음_cursor를_만든다() {
        given(groupTermRepository.existsById(1L)).willReturn(true);
        given(operationSpaceQueryRepository.findSchedules(eq(1L), eq(OperationSpaceStatus.ONGOING), any(), isNull(),
                eq(OperationSpaceSort.LATEST), isNull(), eq(3)))
                .willReturn(List.of(schedule(10L, "가을 정기공연", LocalDateTime.of(2026, 11, 14, 17, 0)),
                        schedule(11L, "신입생 공연", LocalDateTime.of(2026, 11, 2, 18, 0))));
        given(operationSpaceQueryRepository.findMeetings(eq(1L), eq(OperationSpaceStatus.ONGOING), isNull(),
                eq(OperationSpaceSort.LATEST), isNull(), eq(3)))
                .willReturn(List.of(meeting(3L, "운영진 회의", LocalDate.of(2026, 11, 7))));

        OperationSpaceListResponse response = operationSpaceService.listOperationSpaces(
                1L, OperationSpaceStatus.ONGOING, null, null, null, null, 2);

        assertThat(response.getItems()).extracting("type")
                .containsExactly(OperationSpaceType.SCHEDULE, OperationSpaceType.MEETING);
        assertThat(response.getItems()).extracting("id").containsExactly(10L, 3L);
        assertThat(response.getItems().get(1).getDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(response.getNextCursor()).isNotNull();

        // 다음 요청에서 cursor가 회의 3번(유형 순서 1)으로 풀리는지 확인한다
        given(operationSpaceQueryRepository.findSchedules(eq(1L), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(operationSpaceQueryRepository.findMeetings(eq(1L), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());
        operationSpaceService.listOperationSpaces(1L, OperationSpaceStatus.ONGOING, null, null, null,
                response.getNextCursor(), 2);
        ArgumentCaptor<OperationSpaceCursor> captor = ArgumentCaptor.forClass(OperationSpaceCursor.class);
        verify(operationSpaceQueryRepository, times(2)).findMeetings(eq(1L), any(), any(), any(), captor.capture(), anyInt());
        assertThat(captor.getValue().sortValue()).isEqualTo("2026-11-07T00:00");
        assertThat(captor.getValue().typeOrder()).isEqualTo(OperationSpaceQueryRepository.MEETING_TYPE_ORDER);
        assertThat(captor.getValue().id()).isEqualTo(3L);
    }

    @Test
    void listOperationSpaces_SCHEDULE_유형이면_회의록을_조회하지_않는다() {
        given(groupTermRepository.existsById(1L)).willReturn(true);
        given(operationSpaceQueryRepository.findSchedules(eq(1L), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());

        OperationSpaceListResponse response = operationSpaceService.listOperationSpaces(
                1L, OperationSpaceStatus.PAST, OperationSpaceType.SCHEDULE, null, OperationSpaceSort.NAME, null, null);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
        verify(operationSpaceQueryRepository, never()).findMeetings(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void listOperationSpaces_이름순이면_제목_가나다순으로_합친다() {
        given(groupTermRepository.existsById(1L)).willReturn(true);
        given(operationSpaceQueryRepository.findSchedules(eq(1L), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(schedule(10L, "다 공연", LocalDateTime.of(2026, 11, 1, 10, 0))));
        given(operationSpaceQueryRepository.findMeetings(eq(1L), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(meeting(3L, "가 회의", LocalDate.of(2026, 12, 1)),
                        meeting(4L, "나 회의", LocalDate.of(2026, 10, 1))));

        OperationSpaceListResponse response = operationSpaceService.listOperationSpaces(
                1L, OperationSpaceStatus.ONGOING, null, null, OperationSpaceSort.NAME, null, null);

        assertThat(response.getItems()).extracting("title").containsExactly("가 회의", "나 회의", "다 공연");
    }

    @Test
    void listOperationSpaces_잘못된_cursor면_SPACE_400_INVALID_CURSOR() {
        given(groupTermRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> operationSpaceService.listOperationSpaces(
                1L, OperationSpaceStatus.ONGOING, null, null, null, "깨진cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SPACE_400_INVALID_CURSOR);
    }

    @Test
    void listOperationSpaces_없는_기수면_SCHEDULE_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> operationSpaceService.listOperationSpaces(
                99L, OperationSpaceStatus.ONGOING, null, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void linkAgendas_어느_일정에도_속하지_않은_안건을_연결한다() {
        Schedule schedule = schedule(10L, "가을 정기공연", LocalDateTime.of(2026, 11, 14, 17, 0));
        given(groupTerm.getId()).willReturn(1L);
        MeetingAgenda first = agenda(12L, meeting(3L, "운영진 회의", LocalDate.of(2026, 11, 7)), "홍보 계획", 0);
        MeetingAgenda second = agenda(13L, first.getMeeting(), "스태프 배치", 1);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(meetingAgendaRepository.findAllById(List.of(12L, 13L))).willReturn(List.of(first, second));
        given(scheduleAgendaLinkRepository.existsByMeetingAgendaIdIn(List.of(12L, 13L))).willReturn(false);
        given(scheduleAgendaLinkRepository.save(any(ScheduleAgendaLink.class))).willAnswer(invocation -> {
            ScheduleAgendaLink saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L + saved.getMeetingAgenda().getId());
            return saved;
        });

        // 같은 안건을 두 번 보내도 한 번만 연결한다
        AgendaLinkCreateResponse response = operationSpaceService.linkAgendas(10L,
                AgendaLinkCreateRequest.builder().agendaIds(List.of(12L, 13L, 12L)).build());

        assertThat(response.getLinks()).extracting("agendaId").containsExactly(12L, 13L);
        assertThat(response.getLinks()).extracting("linkId").containsExactly(112L, 113L);
    }

    @Test
    void linkAgendas_이미_연결된_안건이면_SPACE_409_AGENDA_ALREADY_LINKED() {
        given(groupTerm.getId()).willReturn(1L);
        given(scheduleRepository.findById(10L))
                .willReturn(Optional.of(schedule(10L, "공연", LocalDateTime.of(2026, 11, 14, 17, 0))));
        MeetingAgenda agenda = agenda(12L, meeting(3L, "운영진 회의", LocalDate.of(2026, 11, 7)), "홍보", 0);
        given(meetingAgendaRepository.findAllById(List.of(12L))).willReturn(List.of(agenda));
        given(scheduleAgendaLinkRepository.existsByMeetingAgendaIdIn(List.of(12L))).willReturn(true);

        assertThatThrownBy(() -> operationSpaceService.linkAgendas(10L,
                AgendaLinkCreateRequest.builder().agendaIds(List.of(12L)).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SPACE_409_AGENDA_ALREADY_LINKED);
        verify(scheduleAgendaLinkRepository, never()).save(any());
    }

    @Test
    void linkAgendas_일정_소속_회의록의_안건이면_SPACE_409_AGENDA_ALREADY_LINKED() {
        given(groupTerm.getId()).willReturn(1L);
        Schedule schedule = schedule(10L, "공연", LocalDateTime.of(2026, 11, 14, 17, 0));
        Schedule otherSchedule = schedule(11L, "다른 공연", LocalDateTime.of(2026, 11, 20, 17, 0));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        Meeting ownedMeeting = meeting(3L, "다른 공연 회의", LocalDate.of(2026, 11, 7));
        ReflectionTestUtils.setField(ownedMeeting, "schedule", otherSchedule);
        given(meetingAgendaRepository.findAllById(List.of(12L)))
                .willReturn(List.of(agenda(12L, ownedMeeting, "곡 순서", 0)));

        assertThatThrownBy(() -> operationSpaceService.linkAgendas(10L,
                AgendaLinkCreateRequest.builder().agendaIds(List.of(12L)).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SPACE_409_AGENDA_ALREADY_LINKED);
    }

    @Test
    void linkAgendas_없거나_다른_기수의_안건이면_SPACE_404_AGENDA_NOT_FOUND() {
        given(scheduleRepository.findById(10L))
                .willReturn(Optional.of(schedule(10L, "공연", LocalDateTime.of(2026, 11, 14, 17, 0))));
        given(meetingAgendaRepository.findAllById(List.of(12L, 99L)))
                .willReturn(List.of(agenda(12L, meeting(3L, "회의", LocalDate.of(2026, 11, 7)), "홍보", 0)));

        assertThatThrownBy(() -> operationSpaceService.linkAgendas(10L,
                AgendaLinkCreateRequest.builder().agendaIds(List.of(12L, 99L)).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SPACE_404_AGENDA_NOT_FOUND);
    }

    @Test
    void linkAgendas_없는_일정이면_SCHEDULE_404_NOT_FOUND() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> operationSpaceService.linkAgendas(99L,
                AgendaLinkCreateRequest.builder().agendaIds(List.of(12L)).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
    }

    @Test
    void listLinkedAgendas_회의_상태와_결정_사항_입력_여부를_함께_내려준다() {
        Schedule schedule = schedule(10L, "공연", LocalDateTime.of(2026, 11, 14, 17, 0));
        Meeting ended = meeting(3L, "운영진 회의", LocalDate.of(2026, 11, 7));
        ReflectionTestUtils.setField(ended, "status", MeetingStatus.COMPLETED);
        MeetingAgenda withDecision = agenda(12L, ended, "홍보 계획", 1);
        ReflectionTestUtils.setField(withDecision, "decision", "릴스 3개");
        MeetingAgenda blankDecision = agenda(13L, ended, "스태프 배치", 2);
        ReflectionTestUtils.setField(blankDecision, "decision", "  ");
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleAgendaLinkRepository.findLinkedAgendas(10L))
                .willReturn(List.of(link(7L, schedule, withDecision), link(8L, schedule, blankDecision)));

        LinkedAgendaListResponse response = operationSpaceService.listLinkedAgendas(10L);

        LinkedAgendaResponse first = response.getLinks().get(0);
        assertThat(first.getLinkId()).isEqualTo(7L);
        assertThat(first.getMeetingTitle()).isEqualTo("운영진 회의");
        assertThat(first.getMeetingStatus()).isEqualTo(MeetingStatus.COMPLETED);
        assertThat(first.getAgendaOrderNo()).isEqualTo(1);
        assertThat(first.getHasDecision()).isTrue();
        assertThat(response.getLinks().get(1).getHasDecision()).isFalse();
    }

    @Test
    void unlinkAgenda_이_일정의_연결만_지운다() {
        Schedule schedule = schedule(10L, "공연", LocalDateTime.of(2026, 11, 14, 17, 0));
        ScheduleAgendaLink link = link(7L, schedule, agenda(12L, meeting(3L, "회의", LocalDate.of(2026, 11, 7)), "홍보", 0));
        given(scheduleAgendaLinkRepository.findByIdAndScheduleId(7L, 10L)).willReturn(Optional.of(link));

        operationSpaceService.unlinkAgenda(10L, 7L);

        verify(scheduleAgendaLinkRepository).delete(link);
    }

    @Test
    void unlinkAgenda_다른_일정의_연결이면_SPACE_404_LINK_NOT_FOUND() {
        given(scheduleAgendaLinkRepository.findByIdAndScheduleId(7L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> operationSpaceService.unlinkAgenda(10L, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SPACE_404_LINK_NOT_FOUND);
        verify(scheduleAgendaLinkRepository, never()).delete(any());
    }

    private Schedule schedule(Long id, String title, LocalDateTime startAt) {
        Schedule schedule = Schedule.create(groupTerm, null, title, "RED", false, startAt, startAt.plusHours(3),
                null, null, true);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private Meeting meeting(Long id, String title, LocalDate date) {
        Meeting meeting = Meeting.create(groupTerm, null, mock(MeetingCategory.class), title, date, null, null);
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }

    private MeetingAgenda agenda(Long id, Meeting meeting, String content, int orderNo) {
        MeetingAgenda agenda = MeetingAgenda.create(meeting, content, orderNo);
        ReflectionTestUtils.setField(agenda, "id", id);
        return agenda;
    }

    private ScheduleAgendaLink link(Long id, Schedule schedule, MeetingAgenda agenda) {
        ScheduleAgendaLink link = ScheduleAgendaLink.create(schedule, agenda);
        ReflectionTestUtils.setField(link, "id", id);
        return link;
    }
}
