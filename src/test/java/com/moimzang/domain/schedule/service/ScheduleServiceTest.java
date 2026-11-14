package com.moimzang.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.mapper.ScheduleMapper;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleCategoryRepository scheduleCategoryRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final ScheduleMapper scheduleMapper = Mappers.getMapper(ScheduleMapper.class);

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, scheduleCategoryRepository, groupTermRepository,
                scheduleMapper);
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

    private ScheduleCategory category(Long id, GroupTerm groupTerm, String name) {
        ScheduleCategory category = ScheduleCategory.create(groupTerm, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
