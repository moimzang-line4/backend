package com.moimzang.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
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
import org.mockito.Mock;
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
}
