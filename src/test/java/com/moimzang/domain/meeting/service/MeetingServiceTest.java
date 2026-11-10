package com.moimzang.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
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

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final MeetingMapper meetingMapper = Mappers.getMapper(MeetingMapper.class);

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
                meetingRepository, meetingAgendaRepository, groupTermRepository, meetingCategoryRepository, meetingMapper);
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
    void listMeetings_정상_조회하면_카테고리명과_안건개수를_포함한_목록을_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(category.getName()).willReturn("운영진 회의");
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, category, "가을 정기공연 운영진 회의",
                LocalDate.of(2026, 11, 7), "김가은, 홍보팀", "가을 공연 준비 논의");
        ReflectionTestUtils.setField(meeting, "id", 10L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(meeting));
        given(meetingAgendaRepository.countByMeetingIds(List.of(10L))).willReturn(Map.of(10L, 3L));

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, null);

        assertThat(response.getMeetings()).hasSize(1);
        assertThat(response.getMeetings().get(0).getMeetingId()).isEqualTo(10L);
        assertThat(response.getMeetings().get(0).getCategoryName()).isEqualTo("운영진 회의");
        assertThat(response.getMeetings().get(0).getAgendaCount()).isEqualTo(3L);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listMeetings_기수가_없으면_MEETING_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.listMeetings(1L, null, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void listMeetings_cursor_형식이_올바르지_않으면_MEETING_400_INVALID_CURSOR를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        assertThatThrownBy(() -> meetingService.listMeetings(1L, null, null, null, "잘못된cursor", null))
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
        Meeting newer = Meeting.create(groupTerm, category, "최근 회의",
                LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(newer, "id", 20L);
        Meeting older = Meeting.create(groupTerm, category, "이전 회의",
                LocalDate.of(2026, 10, 1), null, null);
        ReflectionTestUtils.setField(older, "id", 10L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(newer, older));
        given(meetingAgendaRepository.countByMeetingIds(List.of(20L))).willReturn(Map.of());

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, 1);

        assertThat(response.getMeetings()).hasSize(1);
        assertThat(response.getMeetings().get(0).getMeetingId()).isEqualTo(20L);
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void listMeetings_결과가_없으면_빈_목록과_null_nextCursor를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        MeetingListResponse response = meetingService.listMeetings(1L, MeetingStatus.COMPLETED, null, null, null, null);

        assertThat(response.getMeetings()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listMeetings_안건이_없는_회의는_agendaCount가_0이다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        Meeting meeting = Meeting.create(groupTerm, category, "안건 없는 회의",
                LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(meeting, "id", 30L);

        given(meetingRepository.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(meeting));
        given(meetingAgendaRepository.countByMeetingIds(List.of(30L))).willReturn(Map.of());

        MeetingListResponse response = meetingService.listMeetings(1L, null, null, null, null, null);

        assertThat(response.getMeetings().get(0).getAgendaCount()).isEqualTo(0L);
    }

    @Test
    void getMeetingDetail_정상_조회하면_안건_목록과_진행률을_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MeetingCategory category = mock(MeetingCategory.class);
        given(category.getId()).willReturn(1L);
        Meeting meeting = Meeting.create(groupTerm, category, "가을 정기공연 운영진 회의",
                LocalDate.of(2026, 11, 7), "김가은, 홍보팀", "가을 공연 준비 논의");
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        MeetingAgenda done = MeetingAgenda.create(meeting, "관객 참여 이벤트", 0);
        ReflectionTestUtils.setField(done, "status", AgendaStatus.COMPLETED);
        MeetingAgenda pending = MeetingAgenda.create(meeting, "공연 홍보 계획", 1);
        given(meetingAgendaRepository.findByMeetingIdOrderByOrderNoAsc(10L))
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
        Meeting meeting = Meeting.create(groupTerm, category, "안건 없는 회의", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingAgendaRepository.findByMeetingIdOrderByOrderNoAsc(10L)).willReturn(List.of());

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
        Meeting meeting = Meeting.create(groupTerm, category, "원래 제목",
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
        Meeting meeting = Meeting.create(groupTerm, category, "제목", LocalDate.now(), null, null);
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
        Meeting meeting = Meeting.create(groupTerm, category, "제목", LocalDate.now(), null, null);
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
        Meeting meeting = Meeting.create(groupTerm, category, "제목", LocalDate.now(), null, null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        meetingService.deleteMeeting(10L);

        InOrder inOrder = Mockito.inOrder(meetingAgendaRepository, meetingRepository);
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
}
