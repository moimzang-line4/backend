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
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.exception.MeetingErrorCode;
import com.moimzang.domain.meeting.repository.MeetingCategoryRepository;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
                meetingRepository, meetingAgendaRepository, groupTermRepository, meetingCategoryRepository);
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
}
