package com.moimzang.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingSummaryUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingEndResponse;
import com.moimzang.domain.meeting.dto.response.MeetingLinkableAgendaListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryGenerateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingUpdateResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.mapper.MeetingMapper;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.exception.MeetingErrorCode;
import com.moimzang.domain.meeting.repository.MeetingCategoryRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingAgendaRepository meetingAgendaRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private MeetingCategoryRepository meetingCategoryRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;

    // 외부 연동이라 mock한다
    @Mock
    private AiClient aiClient;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final MeetingMapper meetingMapper = Mappers.getMapper(MeetingMapper.class);

    // 순수 계산 로직이라 mock하지 않는다
    private final MeetingSummaryPromptBuilder summaryPromptBuilder = new MeetingSummaryPromptBuilder();

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(meetingRepository, meetingAgendaRepository, groupTermRepository,
                meetingCategoryRepository, scheduleRepository, scheduleAgendaLinkRepository,
                meetingMapper, aiClient, summaryPromptBuilder);
    }

    @Test
    void createMeeting_정상_요청이면_회의와_안건을_함께_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("가을 정기공연 운영진 회의")
                .meetingDate(LocalDate.of(2026, 11, 7))
                .categoryId(1L)
                .attendees("김가은, 홍보팀")
                .agendas(List.of("관객 참여 이벤트", "공연 홍보 계획"))
                .build();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        // 안건마다 save가 한 번씩 호출되므로 호출될 때마다 다른 id를 부여한다
        AtomicLong agendaId = new AtomicLong(0);
        given(meetingAgendaRepository.save(any(MeetingAgenda.class))).willAnswer(invocation -> {
            MeetingAgenda saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", agendaId.incrementAndGet());
            return saved;
        });

        MeetingCreateResponse response = meetingService.createMeeting(1L, request);

        assertThat(response.getMeetingId()).isEqualTo(100L);
        assertThat(response.getAgendaIds()).containsExactly(1L, 2L);
    }

    @Test
    void createMeeting_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("회의")
                .meetingDate(LocalDate.now())
                .categoryId(1L)
                .agendas(List.of("안건1"))
                .build();
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.createMeeting(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void createMeeting_카테고리가_없으면_MEETING_404_CATEGORY_NOT_FOUND를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("회의")
                .meetingDate(LocalDate.now())
                .categoryId(999L)
                .agendas(List.of("안건1"))
                .build();
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.createMeeting(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_CATEGORY_NOT_FOUND);
    }

    @Test
    void createMeeting_scheduleId를_보내면_그_일정에_속한_회의로_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Schedule schedule = mock(Schedule.class);
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("가을 정기공연 운영진 회의")
                .meetingDate(LocalDate.of(2026, 11, 7))
                .categoryId(1L)
                .scheduleId(5L)
                .agendas(List.of("관객 참여 이벤트"))
                .build();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(scheduleRepository.findByIdAndGroupTermId(5L, 1L)).willReturn(Optional.of(schedule));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        given(meetingAgendaRepository.save(any(MeetingAgenda.class))).willAnswer(invocation -> {
            MeetingAgenda saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        meetingService.createMeeting(1L, request);

        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        Mockito.verify(meetingRepository).save(captor.capture());
        assertThat(captor.getValue().getSchedule()).isSameAs(schedule);
    }

    @Test
    void createMeeting_scheduleId가_없으면_일정과_무관한_회의로_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("홍보팀 회의")
                .meetingDate(LocalDate.of(2026, 11, 7))
                .categoryId(1L)
                .agendas(List.of("홍보 일정"))
                .build();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        given(meetingAgendaRepository.save(any(MeetingAgenda.class))).willAnswer(invocation -> {
            MeetingAgenda saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        meetingService.createMeeting(1L, request);

        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        Mockito.verify(meetingRepository).save(captor.capture());
        assertThat(captor.getValue().getSchedule()).isNull();
        Mockito.verify(scheduleRepository, Mockito.never()).findByIdAndGroupTermId(any(), any());
    }

    @Test
    void createMeeting_다른_기수의_일정이거나_없는_일정이면_MEETING_404_SCHEDULE_NOT_FOUND를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title("가을 정기공연 운영진 회의")
                .meetingDate(LocalDate.of(2026, 11, 7))
                .categoryId(1L)
                .scheduleId(999L)
                .agendas(List.of("관객 참여 이벤트"))
                .build();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.findById(1L)).willReturn(Optional.of(category));
        given(scheduleRepository.findByIdAndGroupTermId(999L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.createMeeting(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_SCHEDULE_NOT_FOUND);
    }

    @Test
    void listMeetings_정상_조회하면_카테고리명과_안건개수를_포함한_목록을_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(category.getName()).willReturn("운영진 회의");
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, null, category, "가을 정기공연 운영진 회의",
                LocalDate.of(2026, 11, 7), "김가은, 홍보팀", "가을 공연 준비 논의");
        ReflectionTestUtils.setField(meeting, "id", 10L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(meeting));
        given(meetingAgendaRepository.countByMeetingIds(List.of(10L))).willReturn(Map.of(10L, 3L));

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, null, null);

        assertThat(response.getMeetings()).hasSize(1);
        assertThat(response.getMeetings().get(0).getMeetingId()).isEqualTo(10L);
        assertThat(response.getMeetings().get(0).getCategoryName()).isEqualTo("운영진 회의");
        assertThat(response.getMeetings().get(0).getAgendaCount()).isEqualTo(3L);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listMeetings_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.listMeetings(1L, null, null, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void listMeetings_cursor_형식이_올바르지_않으면_MEETING_400_INVALID_CURSOR를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        assertThatThrownBy(() -> meetingService.listMeetings(1L, null, null, null, null, "잘못된cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_400_INVALID_CURSOR);
    }

    @Test
    void listMeetings_다음_페이지가_있으면_nextCursor를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        // size=1(limit=1)인데 limit+1건이 조회되면 마지막 1건은 다음 페이지 존재를 알리는 용도다
        Meeting newer = Meeting.create(groupTerm, null, category, "최근 회의",
                LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(newer, "id", 20L);
        Meeting older = Meeting.create(groupTerm, null, category, "이전 회의",
                LocalDate.of(2026, 10, 1), null, null);
        ReflectionTestUtils.setField(older, "id", 10L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(newer, older));
        given(meetingAgendaRepository.countByMeetingIds(List.of(20L))).willReturn(Map.of());

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, null, 1);

        assertThat(response.getMeetings()).hasSize(1);
        assertThat(response.getMeetings().get(0).getMeetingId()).isEqualTo(20L);
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void listMeetings_결과가_없으면_빈_목록과_null_nextCursor를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        MeetingListResponse response = meetingService.listMeetings(1L, MeetingStatus.COMPLETED, null, null, null, null, null);

        assertThat(response.getMeetings()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listMeetings_안건이_없는_회의는_agendaCount가_0이다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, null, category, "안건 없는 회의",
                LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(meeting, "id", 30L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(meeting));
        given(meetingAgendaRepository.countByMeetingIds(List.of(30L))).willReturn(Map.of());

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, null, null);

        assertThat(response.getMeetings().get(0).getAgendaCount()).isEqualTo(0L);
    }

    @Test
    void listMeetings_scheduleId를_주면_그_일정_조건으로_조회한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        meetingService.listMeetings(1L, null, null, null, 5L, null, null);

        Mockito.verify(meetingRepository)
                .search(eq(1L), isNull(), isNull(), isNull(), eq(5L), isNull(), isNull(), any(Integer.class));
    }

    @Test
    void listLinkableAgendas_첫_페이지면_전체_후보_수를_함께_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, null, category, "가을 정기공연 운영진 회의",
                LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        MeetingAgenda agenda = MeetingAgenda.create(meeting, "관객 참여 이벤트", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);

        given(meetingAgendaRepository.searchLinkable(eq(1L), isNull(), isNull(), isNull(), any(Integer.class)))
                .willReturn(List.of(agenda));
        given(meetingAgendaRepository.countLinkable(1L, null)).willReturn(11L);

        MeetingLinkableAgendaListResponse response = meetingService.listLinkableAgendas(1L, null, null, null);

        assertThat(response.getTotalCount()).isEqualTo(11L);
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getAgendas()).hasSize(1);
        assertThat(response.getAgendas().get(0).getAgendaId()).isEqualTo(3L);
        assertThat(response.getAgendas().get(0).getMeetingId()).isEqualTo(10L);
        assertThat(response.getAgendas().get(0).getMeetingTitle()).isEqualTo("가을 정기공연 운영진 회의");
        assertThat(response.getAgendas().get(0).isHasDecision()).isFalse();
    }

    @Test
    void listLinkableAgendas_다음_페이지가_있으면_마지막_항목으로_nextCursor를_만든다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, null, category, "회의", LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        MeetingAgenda first = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(first, "id", 3L);
        MeetingAgenda second = MeetingAgenda.create(meeting, "안건2", 1);
        ReflectionTestUtils.setField(second, "id", 4L);

        // size + 1건을 받아 다음 페이지 존재를 판단한다
        given(meetingAgendaRepository.searchLinkable(eq(1L), isNull(), isNull(), isNull(), eq(2)))
                .willReturn(List.of(first, second));

        MeetingLinkableAgendaListResponse response = meetingService.listLinkableAgendas(1L, null, null, 1);

        assertThat(response.getAgendas()).hasSize(1);
        // cursor는 (회의 ID, 안건 ID) 복합 키다 — 다음 페이지는 같은 회의의 안건2부터 이어진다
        assertThat(response.getNextCursor()).isEqualTo(CursorCodec.encode("10", 3L));
    }

    @Test
    void listLinkableAgendas_cursor를_주면_회의_ID와_안건_ID로_디코딩해_조회한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingAgendaRepository.searchLinkable(eq(1L), isNull(), eq(10L), eq(3L), any(Integer.class)))
                .willReturn(List.of());

        MeetingLinkableAgendaListResponse response =
                meetingService.listLinkableAgendas(1L, null, CursorCodec.encode("10", 3L), null);

        assertThat(response.getAgendas()).isEmpty();
        // 첫 페이지가 아니면 count 쿼리를 날리지 않는다
        assertThat(response.getTotalCount()).isNull();
        Mockito.verify(meetingAgendaRepository, Mockito.never()).countLinkable(any(), any());
    }

    @Test
    void listLinkableAgendas_cursor_형식이_올바르지_않으면_MEETING_400_INVALID_CURSOR를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        assertThatThrownBy(() -> meetingService.listLinkableAgendas(1L, null, "잘못된cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_400_INVALID_CURSOR);
    }

    @Test
    void listLinkableAgendas_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.listLinkableAgendas(999L, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void getMeetingDetail_정상_조회하면_안건_목록과_진행률을_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(category.getId()).willReturn(1L);
        Meeting meeting = Meeting.create(groupTerm, null, category, "가을 정기공연 운영진 회의",
                LocalDate.of(2026, 11, 7), "김가은, 홍보팀", "가을 공연 준비 논의");
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingAgenda done = MeetingAgenda.create(meeting, "관객 참여 이벤트", 0);
        ReflectionTestUtils.setField(done, "status", AgendaStatus.COMPLETED);
        MeetingAgenda pending = MeetingAgenda.create(meeting, "공연 홍보 계획", 1);
        given(meetingAgendaRepository.findOrderedAgendas(10L))
                .willReturn(List.of(done, pending));

        MeetingDetailResponse response = meetingService.getMeetingDetail(10L);

        assertThat(response.getMeetingId()).isEqualTo(10L);
        assertThat(response.getCategoryId()).isEqualTo(1L);
        assertThat(response.getAgendas()).hasSize(2);
        assertThat(response.getProgress().getDoneCount()).isEqualTo(1);
        assertThat(response.getProgress().getTotalCount()).isEqualTo(2);
        assertThat(response.getProgress().getPercentage()).isEqualTo(50);
    }

    @Test
    void getMeetingDetail_안건이_없으면_진행률_0퍼센트를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "안건 없는 회의", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of());

        MeetingDetailResponse response = meetingService.getMeetingDetail(10L);

        assertThat(response.getProgress().getDoneCount()).isEqualTo(0);
        assertThat(response.getProgress().getTotalCount()).isEqualTo(0);
        assertThat(response.getProgress().getPercentage()).isEqualTo(0);
    }

    @Test
    void getMeetingDetail_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.getMeetingDetail(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void updateMeeting_정상_요청이면_보낸_필드만_반영한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "원래 제목",
                LocalDate.of(2026, 11, 7), "원래 참석자", "원래 요약");
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingUpdateRequest request = MeetingUpdateRequest.builder()
                .title("바뀐 제목")
                .build();

        MeetingUpdateResponse response = meetingService.updateMeeting(10L, request);

        assertThat(response.getMeetingId()).isEqualTo(10L);
        assertThat(meeting.getTitle()).isEqualTo("바뀐 제목");
        assertThat(meeting.getAttendees()).isEqualTo("원래 참석자");
        assertThat(meeting.getListSummary()).isEqualTo("원래 요약");
    }

    @Test
    void updateMeeting_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.updateMeeting(999L, MeetingUpdateRequest.builder().build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void updateMeeting_이미_종료된_회의면_MEETING_409_ALREADY_ENDED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.updateMeeting(10L, MeetingUpdateRequest.builder().title("새 제목").build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
    }

    @Test
    void updateMeeting_카테고리가_없으면_MEETING_404_CATEGORY_NOT_FOUND를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingCategoryRepository.findById(999L)).willReturn(Optional.empty());

        MeetingUpdateRequest request = MeetingUpdateRequest.builder().categoryId(999L).build();

        assertThatThrownBy(() -> meetingService.updateMeeting(10L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_CATEGORY_NOT_FOUND);
    }

    @Test
    void deleteMeeting_정상_요청이면_안건을_먼저_지우고_회의를_지운다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        meetingService.deleteMeeting(10L);

        // 연결이 남아있으면 FK가 안건 삭제를 막으므로 링크 → 안건 → 회의 순서여야 한다
        InOrder inOrder = Mockito.inOrder(scheduleAgendaLinkRepository, meetingAgendaRepository, meetingRepository);
        inOrder.verify(scheduleAgendaLinkRepository).deleteAllByMeetingId(10L);
        inOrder.verify(meetingAgendaRepository).deleteAllByMeetingId(10L);
        inOrder.verify(meetingRepository).delete(meeting);
    }

    @Test
    void deleteMeeting_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.deleteMeeting(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void listCategories_정상_조회하면_카테고리_목록을_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        MeetingCategory category = MeetingCategory.create(groupTerm, "공연");
        ReflectionTestUtils.setField(category, "id", 1L);
        given(meetingCategoryRepository.findByGroupTermIdOrderByIdAsc(1L)).willReturn(List.of(category));

        MeetingCategoryListResponse response = meetingService.listCategories(1L);

        assertThat(response.getCategories()).hasSize(1);
        assertThat(response.getCategories().get(0).getCategoryId()).isEqualTo(1L);
        assertThat(response.getCategories().get(0).getName()).isEqualTo("공연");
    }

    @Test
    void listCategories_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.listCategories(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void createCategory_정상_요청이면_카테고리를_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingCategoryRepository.save(any(MeetingCategory.class))).willAnswer(invocation -> {
            MeetingCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        MeetingCategoryResponse response = meetingService.createCategory(
                1L, MeetingCategoryCreateRequest.builder().name("뒤풀이").build());

        assertThat(response.getCategoryId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("뒤풀이");
    }

    @Test
    void createCategory_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.createCategory(
                1L, MeetingCategoryCreateRequest.builder().name("뒤풀이").build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void addAgenda_정상_요청이면_마지막_orderNo_다음으로_추가한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingAgenda last = MeetingAgenda.create(meeting, "안건2", 1);
        given(meetingAgendaRepository.findLastAgendas(eq(10L), any())).willReturn(List.of(last));
        given(meetingAgendaRepository.save(any(MeetingAgenda.class))).willAnswer(invocation -> {
            MeetingAgenda saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        MeetingAgendaCreateResponse response = meetingService.addAgenda(
                10L, MeetingAgendaCreateRequest.builder().content("안건3").build());

        assertThat(response.getAgendaId()).isEqualTo(99L);
        assertThat(response.getOrderNo()).isEqualTo(2);
    }

    @Test
    void addAgenda_기존_안건이_없으면_orderNo_0으로_추가한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingAgendaRepository.findLastAgendas(eq(10L), any())).willReturn(List.of());
        given(meetingAgendaRepository.save(any(MeetingAgenda.class))).willAnswer(invocation -> invocation.getArgument(0));

        MeetingAgendaCreateResponse response = meetingService.addAgenda(
                10L, MeetingAgendaCreateRequest.builder().content("첫 안건").build());

        assertThat(response.getOrderNo()).isEqualTo(0);
    }

    @Test
    void addAgenda_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.addAgenda(
                999L, MeetingAgendaCreateRequest.builder().content("안건").build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void addAgenda_이미_종료된_회의면_MEETING_409_ALREADY_ENDED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.addAgenda(
                10L, MeetingAgendaCreateRequest.builder().content("안건").build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
    }

    @Test
    void updateAgendaContent_다음_안건이면_status를_COMPLETED로_저장하고_진행률을_갱신한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .decision("경품은 홍보팀이 준비")
                .status(AgendaStatus.COMPLETED)
                .build();

        MeetingAgendaUpdateResponse response = meetingService.updateAgendaContent(3L, request);

        assertThat(response.getStatus()).isEqualTo(AgendaStatus.COMPLETED);
        assertThat(agenda.getDecision()).isEqualTo("경품은 홍보팀이 준비");
        assertThat(response.getProgress().getDoneCount()).isEqualTo(1);
        assertThat(response.getProgress().getTotalCount()).isEqualTo(1);
        assertThat(response.getProgress().getPercentage()).isEqualTo(100);
    }

    @Test
    void updateAgendaContent_중단하기면_status_없이_내용만_저장한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .discussionNote("논의 중간 메모")
                .build();

        MeetingAgendaUpdateResponse response = meetingService.updateAgendaContent(3L, request);

        assertThat(response.getStatus()).isEqualTo(AgendaStatus.PENDING);
        assertThat(agenda.getDiscussionNote()).isEqualTo("논의 중간 메모");
    }

    @Test
    void updateAgendaContent_content만_보내면_내용만_바뀌고_나머지는_유지된다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "원래 내용", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .content("고친 내용")
                .build();

        meetingService.updateAgendaContent(3L, request);

        assertThat(agenda.getContent()).isEqualTo("고친 내용");
        assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.PENDING);
    }

    @Test
    void updateAgendaContent_안건이_없으면_AGENDA_404_NOT_FOUND를_던진다() {
        given(meetingAgendaRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.updateAgendaContent(999L, MeetingAgendaUpdateRequest.builder().build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.AGENDA_404_NOT_FOUND);
    }

    @Test
    void updateAgendaContent_회의가_종료됐으면_MEETING_409_ALREADY_ENDED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));

        assertThatThrownBy(() -> meetingService.updateAgendaContent(3L, MeetingAgendaUpdateRequest.builder().build()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
    }

    @Test
    void updateAgendaContent_완료된_안건을_다른_상태로_되돌리려_하면_AGENDA_409_ALREADY_COMPLETED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        ReflectionTestUtils.setField(agenda, "status", AgendaStatus.COMPLETED);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .status(AgendaStatus.PENDING)
                .build();

        assertThatThrownBy(() -> meetingService.updateAgendaContent(3L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.AGENDA_409_ALREADY_COMPLETED);
    }

    @Test
    void updateAgendaContent_완료된_안건이어도_status_없이_내용만_고치는건_허용한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        ReflectionTestUtils.setField(agenda, "status", AgendaStatus.COMPLETED);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .decision("결정 사항 보완")
                .build();

        MeetingAgendaUpdateResponse response = meetingService.updateAgendaContent(3L, request);

        assertThat(response.getStatus()).isEqualTo(AgendaStatus.COMPLETED);
        assertThat(agenda.getDecision()).isEqualTo("결정 사항 보완");
    }

    @Test
    void updateAgendaContent_완료된_안건에_status_COMPLETED를_다시_보내도_허용한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        ReflectionTestUtils.setField(agenda, "status", AgendaStatus.COMPLETED);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        MeetingAgendaUpdateRequest request = MeetingAgendaUpdateRequest.builder()
                .status(AgendaStatus.COMPLETED)
                .build();

        MeetingAgendaUpdateResponse response = meetingService.updateAgendaContent(3L, request);

        assertThat(response.getStatus()).isEqualTo(AgendaStatus.COMPLETED);
    }

    @Test
    void deleteAgenda_정상_요청이면_삭제한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.countByMeetingId(10L)).willReturn(2L);

        meetingService.deleteAgenda(3L);

        // 일정에 연결된 안건이면 그 연결을 먼저 끊어야 FK에 걸리지 않는다
        InOrder inOrder = Mockito.inOrder(scheduleAgendaLinkRepository, meetingAgendaRepository);
        inOrder.verify(scheduleAgendaLinkRepository).deleteAllByMeetingAgendaId(3L);
        inOrder.verify(meetingAgendaRepository).delete(agenda);
    }

    @Test
    void deleteAgenda_안건이_없으면_AGENDA_404_NOT_FOUND를_던진다() {
        given(meetingAgendaRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.deleteAgenda(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.AGENDA_404_NOT_FOUND);
    }

    @Test
    void deleteAgenda_회의가_종료됐으면_MEETING_409_ALREADY_ENDED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));

        assertThatThrownBy(() -> meetingService.deleteAgenda(3L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
    }

    @Test
    void deleteAgenda_안건이_1개뿐이면_AGENDA_409_AT_LEAST_ONE_REQUIRED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "안건1", 0);
        ReflectionTestUtils.setField(agenda, "id", 3L);
        given(meetingAgendaRepository.findById(3L)).willReturn(Optional.of(agenda));
        given(meetingAgendaRepository.countByMeetingId(10L)).willReturn(1L);

        assertThatThrownBy(() -> meetingService.deleteAgenda(3L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.AGENDA_409_AT_LEAST_ONE_REQUIRED);
    }

    @Test
    void endMeeting_정상_요청이면_일부만_완료돼도_종료한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingAgendaRepository.countByMeetingIdAndStatus(10L, AgendaStatus.COMPLETED)).willReturn(1L);
        given(meetingAgendaRepository.countByMeetingId(10L)).willReturn(3L);

        MeetingEndResponse response = meetingService.endMeeting(10L);

        assertThat(response.getMeetingId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        assertThat(response.getEndedAt()).isNotNull();
        assertThat(response.getDoneAgendaCount()).isEqualTo(1L);
        assertThat(response.getTotalAgendaCount()).isEqualTo(3L);
        assertThat(meeting.isEnded()).isTrue();
    }

    @Test
    void endMeeting_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.endMeeting(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void endMeeting_이미_종료된_회의면_MEETING_409_ALREADY_ENDED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.endMeeting(10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
    }

    @Test
    void generateSummary_정상_요청이면_AI_요약을_저장하고_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "가을 정기공연 운영진 회의", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingAgenda agenda = MeetingAgenda.create(meeting, "관객 참여 이벤트", 0);
        ReflectionTestUtils.setField(agenda, "decision", "경품은 홍보팀이 준비");
        given(meetingAgendaRepository.findOrderedAgendas(10L)).willReturn(List.of(agenda));

        given(aiClient.generateContent(any())).willReturn("가을 정기공연 준비를 위해 관객 참여 이벤트를 논의했어요.");

        MeetingSummaryGenerateResponse response = meetingService.generateSummary(10L);

        assertThat(response.getSummarizedAgendaCount()).isEqualTo(1);
        assertThat(response.getSummaryContent()).isEqualTo("가을 정기공연 준비를 위해 관객 참여 이벤트를 논의했어요.");
        assertThat(response.getSummaryGeneratedAt()).isNotNull();
        assertThat(meeting.getSummaryContent()).isEqualTo("가을 정기공연 준비를 위해 관객 참여 이벤트를 논의했어요.");
    }

    @Test
    void generateSummary_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.generateSummary(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }

    @Test
    void generateSummary_AI_호출이_실패하면_MEETING_502_SUMMARY_FAILED를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingAgendaRepository.findOrderedAgendas(10L))
                .willReturn(List.of(MeetingAgenda.create(meeting, "안건1", 0)));

        willThrow(new AiClientException("AI 호출 실패")).given(aiClient).generateContent(any());

        assertThatThrownBy(() -> meetingService.generateSummary(10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_502_SUMMARY_FAILED);
    }

    @Test
    void updateSummary_정상_요청이면_사용자가_고친_내용을_그대로_저장한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        Meeting meeting = Meeting.create(groupTerm, null, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        meeting.applySummary("AI가 생성한 원본 요약");
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingSummaryUpdateRequest request = MeetingSummaryUpdateRequest.builder()
                .summaryContent("사용자가 고친 요약")
                .build();

        MeetingSummaryUpdateResponse response = meetingService.updateSummary(10L, request);

        assertThat(response.getMeetingId()).isEqualTo(10L);
        assertThat(meeting.getSummaryContent()).isEqualTo("사용자가 고친 요약");
    }

    @Test
    void updateSummary_회의가_없으면_MEETING_404_NOT_FOUND를_던진다() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        MeetingSummaryUpdateRequest request = MeetingSummaryUpdateRequest.builder()
                .summaryContent("요약")
                .build();

        assertThatThrownBy(() -> meetingService.updateSummary(999L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_NOT_FOUND);
    }
}
