package com.moimzang.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryUpdateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleListResponse;
import com.moimzang.domain.schedule.dto.response.UpcomingScheduleListResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.enums.ScheduleColor;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.mapper.ScheduleMapper;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CommonErrorCode;
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
import org.mockito.InOrder;
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

    @Mock
    private ScheduleCascadeDeleter scheduleCascadeDeleter;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final ScheduleMapper scheduleMapper = Mappers.getMapper(ScheduleMapper.class);

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleCategoryRepository, groupTermRepository,
                scheduleRoundRepository, scheduleMapper, scheduleCascadeDeleter);
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
    void updateCategory_이름을_공백을_빼고_바꾼다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTerm.getId()).willReturn(1L);
        ScheduleCategory category = category(5L, groupTerm, "공연");
        given(scheduleCategoryRepository.findById(5L)).willReturn(Optional.of(category));
        given(scheduleCategoryRepository.existsByGroupTermIdAndNameAndIdNot(1L, "정기공연", 5L)).willReturn(false);

        ScheduleCategoryResponse response = scheduleService.updateCategory(5L,
                ScheduleCategoryUpdateRequest.builder().name(" 정기공연 ").build());

        assertThat(category.getName()).isEqualTo("정기공연");
        assertThat(response.getCategoryId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("정기공연");
    }

    @Test
    void updateCategory_같은_기수에_같은_이름이_있으면_SCHEDULE_409_CATEGORY_DUPLICATED() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTerm.getId()).willReturn(1L);
        ScheduleCategory category = category(5L, groupTerm, "공연");
        given(scheduleCategoryRepository.findById(5L)).willReturn(Optional.of(category));
        given(scheduleCategoryRepository.existsByGroupTermIdAndNameAndIdNot(1L, "뒤풀이", 5L)).willReturn(true);

        assertThatThrownBy(() -> scheduleService.updateCategory(5L,
                ScheduleCategoryUpdateRequest.builder().name("뒤풀이").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_409_CATEGORY_DUPLICATED);
        assertThat(category.getName()).isEqualTo("공연");
    }

    @Test
    void updateCategory_없는_카테고리면_SCHEDULE_404_CATEGORY_NOT_FOUND() {
        given(scheduleCategoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.updateCategory(99L,
                ScheduleCategoryUpdateRequest.builder().name("공연").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_CATEGORY_NOT_FOUND);
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

    @Test
    void updateSchedule_꺼진_운영_공간을_켜면_1차_차수를_만든다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", false);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findLastRounds(eq(10L), any())).willReturn(List.of());

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder().createOperationSpace(true).build());

        assertThat(schedule.getHasOperationSpace()).isTrue();
        ArgumentCaptor<ScheduleRound> captor = ArgumentCaptor.forClass(ScheduleRound.class);
        verify(scheduleRoundRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundNo()).isEqualTo(1);
        assertThat(captor.getValue().getLabel()).isEqualTo("1차");
    }

    @Test
    void updateSchedule_운영_공간을_켤_때_이미_차수가_있으면_새로_만들지_않는다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", false);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findLastRounds(eq(10L), any()))
                .willReturn(List.of(ScheduleRound.create(schedule, 1, "1차")));

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder().createOperationSpace(true).build());

        assertThat(schedule.getHasOperationSpace()).isTrue();
        verify(scheduleRoundRepository, never()).save(any());
    }

    @Test
    void updateSchedule_이미_켜진_운영_공간을_다시_켜도_차수를_만들지_않는다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder().createOperationSpace(true).build());

        verify(scheduleRoundRepository, never()).save(any());
    }

    @Test
    void updateSchedule_켜진_운영_공간을_끄려고_하면_SCHEDULE_400_OPERATION_SPACE_CANNOT_DISABLE() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder()
                .title("바뀐 제목")
                .createOperationSpace(false)
                .build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_400_OPERATION_SPACE_CANNOT_DISABLE);
        assertThat(schedule.getTitle()).isEqualTo("가을 정기공연");
    }

    @Test
    void updateSchedule_꺼진_운영_공간에_false를_보내면_그대로_둔다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", false);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        scheduleService.updateSchedule(10L, ScheduleUpdateRequest.builder().createOperationSpace(false).build());

        assertThat(schedule.getHasOperationSpace()).isFalse();
        verify(scheduleRoundRepository, never()).save(any());
    }

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    @Test
    void listSchedules_기간을_하루_전체로_넘기고_다음_페이지가_없으면_nextCursor는_null() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.searchCalendar(eq(1L), eq(NOV_1.atStartOfDay()), eq(NOV_30.atTime(23, 59, 59, 999_999_999)),
                isNull(), isNull(), eq(21))).willReturn(List.of(schedule));

        ScheduleListResponse response = scheduleService.listSchedules(1L, NOV_1, NOV_30, null, null);

        assertThat(response.getSchedules()).hasSize(1);
        assertThat(response.getSchedules().get(0).getScheduleId()).isEqualTo(10L);
        assertThat(response.getSchedules().get(0).getColor()).isEqualTo("RED");
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listSchedules_size보다_많으면_마지막_일정으로_다음_cursor를_만든다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        List<Schedule> fetched = List.of(
                schedule(10L, mock(GroupTerm.class), null, START, END),
                schedule(11L, mock(GroupTerm.class), null, START.plusDays(1), END.plusDays(1)),
                schedule(12L, mock(GroupTerm.class), null, START.plusDays(2), END.plusDays(2)));
        given(scheduleRepository.searchCalendar(eq(1L), any(), any(), isNull(), isNull(), eq(3))).willReturn(fetched);

        ScheduleListResponse response = scheduleService.listSchedules(1L, NOV_1, NOV_30, null, 2);

        assertThat(response.getSchedules()).extracting("scheduleId").containsExactly(10L, 11L);
        CursorCodec next = CursorCodec.decode(response.getNextCursor());
        assertThat(next.getId()).isEqualTo(11L);
        assertThat(LocalDateTime.parse(next.getSortValue())).isEqualTo(START.plusDays(1));
    }

    @Test
    void listSchedules_cursor를_풀어서_다음_페이지를_조회한다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        String cursor = CursorCodec.encode(START.toString(), 10L);
        given(scheduleRepository.searchCalendar(eq(1L), any(), any(), eq(START), eq(10L), eq(21))).willReturn(List.of());

        ScheduleListResponse response = scheduleService.listSchedules(1L, NOV_1, NOV_30, cursor, null);

        assertThat(response.getSchedules()).isEmpty();
    }

    @Test
    void listSchedules_시작일이_종료일보다_늦으면_COMMON_400() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));

        assertThatThrownBy(() -> scheduleService.listSchedules(1L, NOV_30, NOV_1, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void listSchedules_잘못된_cursor면_SCHEDULE_400_INVALID_CURSOR() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));

        assertThatThrownBy(() -> scheduleService.listSchedules(1L, NOV_1, NOV_30, "깨진cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_400_INVALID_CURSOR);
    }

    @Test
    void listSchedules_없는_기수면_SCHEDULE_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.listSchedules(99L, NOV_1, NOV_30, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void listUpcomingSchedules_기본_5개를_조회하고_운영_메뉴_수는_4다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRepository.findUpcomingOperationSchedules(eq(1L), any(LocalDateTime.class), eq(5)))
                .willReturn(List.of(schedule(10L, groupTerm, category(3L, groupTerm, "공연"), START, END)));

        UpcomingScheduleListResponse response = scheduleService.listUpcomingSchedules(1L, null);

        assertThat(response.getSchedules()).hasSize(1);
        assertThat(response.getSchedules().get(0).getScheduleId()).isEqualTo(10L);
        assertThat(response.getSchedules().get(0).getCategoryName()).isEqualTo("공연");
        assertThat(response.getSchedules().get(0).getLocation()).isEqualTo("얼라이브홀");
        assertThat(response.getSchedules().get(0).getOperationMenuCount()).isEqualTo(4);
    }

    @Test
    void listUpcomingSchedules_limit은_최대_20으로_자른다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        given(scheduleRepository.findUpcomingOperationSchedules(eq(1L), any(LocalDateTime.class), eq(20)))
                .willReturn(List.of());

        UpcomingScheduleListResponse response = scheduleService.listUpcomingSchedules(1L, 100);

        assertThat(response.getSchedules()).isEmpty();
    }

    @Test
    void deleteSchedule_하위_데이터를_먼저_지우고_일정을_지운다() {
        Schedule schedule = schedule(10L, mock(GroupTerm.class), null, START, END);
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));

        scheduleService.deleteSchedule(10L);

        InOrder order = inOrder(scheduleCascadeDeleter, scheduleRepository);
        order.verify(scheduleCascadeDeleter).deleteChildren(10L);
        order.verify(scheduleRepository).deleteById(10L);
    }

    @Test
    void deleteSchedule_없는_일정이면_SCHEDULE_404_NOT_FOUND() {
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.deleteSchedule(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
        verify(scheduleCascadeDeleter, never()).deleteChildren(any());
        verify(scheduleRepository, never()).deleteById(any());
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
