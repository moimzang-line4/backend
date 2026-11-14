package com.moimzang.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.enums.ScheduleColor;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.mapper.ScheduleMapper;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleCategoryRepository scheduleCategoryRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final ScheduleMapper scheduleMapper = Mappers.getMapper(ScheduleMapper.class);

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleCategoryRepository, groupTermRepository,
                scheduleRoundRepository, scheduleMapper);
    }

    @Test
    void createCategory_기수에_카테고리를_추가한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleCategoryRepository.existsByGroupTermIdAndName(1L, "공연")).willReturn(false);
        given(scheduleCategoryRepository.save(any(ScheduleCategory.class))).willAnswer(invocation -> {
            ScheduleCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        ScheduleCategoryResponse response = scheduleService.createCategory(1L,
                ScheduleCategoryCreateRequest.builder().name(" 공연 ").build());

        ArgumentCaptor<ScheduleCategory> captor = ArgumentCaptor.forClass(ScheduleCategory.class);
        verify(scheduleCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupTerm()).isSameAs(groupTerm);
        assertThat(captor.getValue().getName()).isEqualTo("공연");
        assertThat(response.getCategoryId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("공연");
    }

    @Test
    void createCategory_같은_이름이_있으면_SCHEDULE_409_CATEGORY_DUPLICATED() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        given(scheduleCategoryRepository.existsByGroupTermIdAndName(1L, "공연")).willReturn(true);

        assertThatThrownBy(() -> scheduleService.createCategory(1L,
                ScheduleCategoryCreateRequest.builder().name("공연").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_409_CATEGORY_DUPLICATED);
        verify(scheduleCategoryRepository, never()).save(any());
    }

    @Test
    void createCategory_없는_기수면_SCHEDULE_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createCategory(99L,
                ScheduleCategoryCreateRequest.builder().name("공연").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void listCategories_기수의_카테고리를_만든_순서대로_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleCategoryRepository.findByGroupTermIdOrderByIdAsc(1L))
                .willReturn(List.of(category(1L, groupTerm, "공연"), category(2L, groupTerm, "뒤풀이")));

        ScheduleCategoryListResponse response = scheduleService.listCategories(1L);

        assertThat(response.getCategories()).extracting("categoryId").containsExactly(1L, 2L);
        assertThat(response.getCategories()).extracting("name").containsExactly("공연", "뒤풀이");
    }

    @Test
    void listCategories_없는_기수면_SCHEDULE_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.listCategories(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
    }

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 14, 17, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 14, 21, 0);

    @Test
    void createSchedule_운영_공간을_켜면_일정과_1차_차수를_함께_만든다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        ScheduleCategory category = category(3L, groupTerm, "공연");
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleCategoryRepository.findByIdAndGroupTermId(3L, 1L)).willReturn(Optional.of(category));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> {
            Schedule saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        ScheduleCreateResponse response = scheduleService.createSchedule(1L, createRequest(3L, true)
                .location("  ")
                .build());

        Schedule saved = captureSavedSchedule();
        assertThat(response.getScheduleId()).isEqualTo(10L);
        assertThat(response.getHasOperationSpace()).isTrue();
        assertThat(saved.getColor()).isEqualTo("RED");
        assertThat(saved.getTitle()).isEqualTo("가을 정기공연");
        assertThat(saved.getScheduleCategory()).isSameAs(category);
        assertThat(saved.getLocation()).isNull();

        ArgumentCaptor<ScheduleRound> roundCaptor = ArgumentCaptor.forClass(ScheduleRound.class);
        verify(scheduleRoundRepository).save(roundCaptor.capture());
        assertThat(roundCaptor.getValue().getSchedule()).isSameAs(saved);
        assertThat(roundCaptor.getValue().getRoundNo()).isEqualTo(1);
        assertThat(roundCaptor.getValue().getLabel()).isEqualTo("1차");
    }

    @Test
    void createSchedule_운영_공간을_끄면_차수를_만들지_않고_카테고리_없이도_등록된다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> invocation.getArgument(0));

        ScheduleCreateResponse response = scheduleService.createSchedule(1L, createRequest(null, false).build());

        assertThat(response.getHasOperationSpace()).isFalse();
        assertThat(captureSavedSchedule().getScheduleCategory()).isNull();
        verify(scheduleRoundRepository, never()).save(any());
    }

    @Test
    void createSchedule_종료가_시작보다_빠르면_SCHEDULE_400_END_BEFORE_START() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));

        assertThatThrownBy(() -> scheduleService.createSchedule(1L, createRequest(null, false)
                .startAt(END).endAt(START).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_400_END_BEFORE_START);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void createSchedule_다른_기수의_카테고리면_SCHEDULE_404_CATEGORY_NOT_FOUND() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        given(scheduleCategoryRepository.findByIdAndGroupTermId(7L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createSchedule(1L, createRequest(7L, false).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_CATEGORY_NOT_FOUND);
    }

    @Test
    void createSchedule_없는_기수면_SCHEDULE_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createSchedule(99L, createRequest(null, false).build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void getSchedule_일정_정보와_카테고리명_D_day를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Schedule schedule = schedule(10L, groupTerm, category(3L, groupTerm, "공연"),
                today.plusDays(9).atTime(17, 0), today.plusDays(9).atTime(21, 0));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        ScheduleDetailResponse response = scheduleService.getSchedule(10L);

        assertThat(response.getScheduleId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("가을 정기공연");
        assertThat(response.getColor()).isEqualTo("RED");
        assertThat(response.getCategoryId()).isEqualTo(3L);
        assertThat(response.getCategoryName()).isEqualTo("공연");
        assertThat(response.getDDay()).isEqualTo(9L);
        assertThat(response.getHasOperationSpace()).isTrue();
    }

    @Test
    void getSchedule_당일_일정은_D_day가_0이고_카테고리가_없으면_null() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null,
                today.atTime(23, 0), today.atTime(23, 30));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        ScheduleDetailResponse response = scheduleService.getSchedule(10L);

        assertThat(response.getDDay()).isZero();
        assertThat(response.getCategoryId()).isNull();
        assertThat(response.getCategoryName()).isNull();
    }

    @Test
    void getSchedule_없는_일정이면_SCHEDULE_404_NOT_FOUND() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedule(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
    }

    @Test
    void updateSchedule_보낸_필드만_수정하고_빈_장소는_지운다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder()
                .color(ScheduleColor.BLUE)
                .location("")
                .build());

        assertThat(schedule.getColor()).isEqualTo("BLUE");
        assertThat(schedule.getLocation()).isNull();
        assertThat(schedule.getTitle()).isEqualTo("가을 정기공연");
        assertThat(schedule.getStartAt()).isEqualTo(START);
    }

    @Test
    void updateSchedule_시작만_바꿔서_종료보다_늦어지면_SCHEDULE_400_END_BEFORE_START() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder()
                .startAt(END.plusHours(1))
                .build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_400_END_BEFORE_START);
        assertThat(schedule.getStartAt()).isEqualTo(START);
    }

    @Test
    void updateSchedule_카테고리는_일정이_속한_기수에서_찾는다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTerm.getId()).willReturn(1L);
        Schedule schedule = schedule(10L, groupTerm, null, START, END);
        ScheduleCategory category = category(3L, groupTerm, "공연");
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleCategoryRepository.findByIdAndGroupTermId(3L, 1L)).willReturn(Optional.of(category));

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder().categoryId(3L).build());

        assertThat(schedule.getScheduleCategory()).isSameAs(category);
    }

    @Test
    void updateSchedule_없는_일정이면_SCHEDULE_404_NOT_FOUND() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.updateSchedule(99L, ScheduleUpdateRequest.builder().title("제목").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
    }

    private ScheduleCreateRequest.ScheduleCreateRequestBuilder createRequest(Long categoryId, boolean operationSpace) {
        return ScheduleCreateRequest.builder()
                .title(" 가을 정기공연 ")
                .color(ScheduleColor.RED)
                .categoryId(categoryId)
                .isAllDay(false)
                .startAt(START)
                .endAt(END)
                .createOperationSpace(operationSpace);
    }

    private Schedule captureSavedSchedule() {
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        return captor.getValue();
    }

    private Schedule schedule(Long id, GroupTerm groupTerm, ScheduleCategory category, LocalDateTime startAt,
                              LocalDateTime endAt) {
        Schedule schedule = Schedule.create(groupTerm, category, "가을 정기공연", "RED", false, startAt, endAt,
                "얼라이브홀", null, true);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private ScheduleCategory category(Long id, GroupTerm groupTerm, String name) {
        ScheduleCategory category = ScheduleCategory.create(groupTerm, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
