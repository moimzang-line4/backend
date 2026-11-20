package com.moimzang.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.group.dto.request.GroupCreateRequest;
import com.moimzang.domain.group.dto.request.GroupUpdateRequest;
import com.moimzang.domain.group.dto.response.GroupCreateResponse;
import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupSummaryListResponse;
import com.moimzang.domain.group.dto.response.GroupSummaryResponse;
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.exception.GroupErrorCode;
import com.moimzang.domain.group.mapper.GroupMapper;
import com.moimzang.domain.group.repository.ClubGroupRepository;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private ClubGroupRepository clubGroupRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final GroupMapper groupMapper = Mappers.getMapper(GroupMapper.class);

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(clubGroupRepository, groupTermRepository, groupMapper);
    }

    @Test
    void createGroup_그룹과_첫_기수를_함께_저장한다() {
        GroupCreateRequest request = GroupCreateRequest.builder()
                .name("  음샘 ")
                .termName("26기 (2026)")
                .build();
        given(clubGroupRepository.save(any(ClubGroup.class))).willAnswer(invocation -> {
            ClubGroup saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        given(groupTermRepository.save(any(GroupTerm.class))).willAnswer(invocation -> {
            GroupTerm saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        GroupCreateResponse response = groupService.createGroup(request);

        assertThat(response.getGroupId()).isEqualTo(1L);
        assertThat(response.getTermId()).isEqualTo(10L);

        ArgumentCaptor<GroupTerm> termCaptor = ArgumentCaptor.forClass(GroupTerm.class);
        verify(groupTermRepository).save(termCaptor.capture());
        GroupTerm savedTerm = termCaptor.getValue();
        assertThat(savedTerm.getName()).isEqualTo("26기 (2026)");
        assertThat(savedTerm.getGroup().getId()).isEqualTo(1L);
        assertThat(savedTerm.getGroup().getName()).isEqualTo("음샘");
    }

    @Test
    void getGroup_그룹명과_기수_목록을_만든_순서대로_반환한다() {
        ClubGroup group = group(1L, "음샘");
        given(clubGroupRepository.findById(1L)).willReturn(Optional.of(group));
        given(groupTermRepository.findAllByGroupIdOrderByIdAsc(1L))
                .willReturn(List.of(term(10L, group, "26기 (2026)"), term(11L, group, "27기 (2027)")));

        GroupDetailResponse response = groupService.getGroup(1L);

        assertThat(response.getGroupId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("음샘");
        assertThat(response.getTerms()).extracting("termId").containsExactly(10L, 11L);
        assertThat(response.getTerms()).extracting("name").containsExactly("26기 (2026)", "27기 (2027)");
    }

    @Test
    void getGroup_없는_그룹이면_GROUP_404_NOT_FOUND() {
        given(clubGroupRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroup(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_404_NOT_FOUND);
    }

    @Test
    void updateGroup_그룹명만_보내면_이름만_바꾸고_현재_기수를_돌려준다() {
        ClubGroup group = group(1L, "음샘");
        given(clubGroupRepository.findById(1L)).willReturn(Optional.of(group));
        given(groupTermRepository.findFirstByGroupIdOrderByIdDesc(1L))
                .willReturn(Optional.of(term(11L, group, "27기 (2027)")));

        GroupUpdateResponse response = groupService.updateGroup(1L,
                GroupUpdateRequest.builder().name("음샘 중앙밴드").build());

        assertThat(group.getName()).isEqualTo("음샘 중앙밴드");
        assertThat(response.getName()).isEqualTo("음샘 중앙밴드");
        assertThat(response.getTermId()).isEqualTo(11L);
        assertThat(response.getTermName()).isEqualTo("27기 (2027)");
        verify(groupTermRepository, never()).save(any(GroupTerm.class));
    }

    @Test
    void updateGroup_기수명을_보내면_새_기수를_만든다() {
        ClubGroup group = group(1L, "음샘");
        given(clubGroupRepository.findById(1L)).willReturn(Optional.of(group));
        given(groupTermRepository.save(any(GroupTerm.class))).willAnswer(invocation -> {
            GroupTerm saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        GroupUpdateResponse response = groupService.updateGroup(1L,
                GroupUpdateRequest.builder().termName(" 28기 (2028) ").build());

        assertThat(group.getName()).isEqualTo("음샘");
        assertThat(response.getTermId()).isEqualTo(12L);
        assertThat(response.getTermName()).isEqualTo("28기 (2028)");
        verify(groupTermRepository, never()).findFirstByGroupIdOrderByIdDesc(any());
    }

    @Test
    void updateGroup_없는_그룹이면_GROUP_404_NOT_FOUND() {
        given(clubGroupRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.updateGroup(99L, GroupUpdateRequest.builder().name("음샘").build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_404_NOT_FOUND);
    }

    @Test
    void getGroupsByIds_요청한_ids_순서를_유지하고_없는_id는_제외한다() {
        ClubGroup group1 = group(1L, "음샘");
        ClubGroup group2 = group(2L, "밴드부");
        given(clubGroupRepository.findAllById(List.of(2L, 1L, 99L))).willReturn(List.of(group1, group2));
        given(groupTermRepository.findAllByGroupIdIn(List.of(2L, 1L)))
                .willReturn(List.of(term(10L, group1, "26기"), term(20L, group2, "1기")));

        GroupSummaryListResponse response = groupService.getGroupsByIds("2,1,99");

        assertThat(response.getGroups()).extracting(GroupSummaryResponse::getGroupId).containsExactly(2L, 1L);
    }

    @Test
    void getGroupsByIds_중복_id는_한_번만_포함한다() {
        ClubGroup group1 = group(1L, "음샘");
        ClubGroup group2 = group(2L, "밴드부");
        given(clubGroupRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(group1, group2));
        given(groupTermRepository.findAllByGroupIdIn(List.of(1L, 2L)))
                .willReturn(List.of(term(10L, group1, "26기"), term(20L, group2, "1기")));

        GroupSummaryListResponse response = groupService.getGroupsByIds("1,1,2,2");

        assertThat(response.getGroups()).extracting(GroupSummaryResponse::getGroupId).containsExactly(1L, 2L);
    }

    @Test
    void getGroupsByIds_기수가_없는_그룹은_currentTerm이_null이다() {
        ClubGroup group = group(1L, "음샘");
        given(clubGroupRepository.findAllById(List.of(1L))).willReturn(List.of(group));
        given(groupTermRepository.findAllByGroupIdIn(List.of(1L))).willReturn(List.of());

        GroupSummaryListResponse response = groupService.getGroupsByIds("1");

        GroupSummaryResponse summary = response.getGroups().get(0);
        assertThat(summary.getGroupId()).isEqualTo(1L);
        assertThat(summary.getName()).isEqualTo("음샘");
        assertThat(summary.getCurrentTermId()).isNull();
        assertThat(summary.getCurrentTermName()).isNull();
    }

    @Test
    void getGroupsByIds_기수가_여러_개면_id가_가장_큰_기수를_현재_기수로_고른다() {
        ClubGroup group = group(1L, "음샘");
        given(clubGroupRepository.findAllById(List.of(1L))).willReturn(List.of(group));
        given(groupTermRepository.findAllByGroupIdIn(List.of(1L)))
                .willReturn(List.of(term(10L, group, "26기"), term(12L, group, "28기"), term(11L, group, "27기")));

        GroupSummaryListResponse response = groupService.getGroupsByIds("1");

        GroupSummaryResponse summary = response.getGroups().get(0);
        assertThat(summary.getCurrentTermId()).isEqualTo(12L);
        assertThat(summary.getCurrentTermName()).isEqualTo("28기");
    }

    @Test
    void getGroupsByIds_ids가_빈_문자열이면_COMMON_400() {
        assertThatThrownBy(() -> groupService.getGroupsByIds(""))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getGroupsByIds_ids가_null이면_COMMON_400() {
        assertThatThrownBy(() -> groupService.getGroupsByIds(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getGroupsByIds_공백만_있는_토큰이_있으면_COMMON_400() {
        assertThatThrownBy(() -> groupService.getGroupsByIds("1, ,2"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getGroupsByIds_숫자가_아닌_값이_있으면_COMMON_400() {
        assertThatThrownBy(() -> groupService.getGroupsByIds("1,abc,2"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getGroupsByIds_중복_제거_후_50개_초과면_COMMON_400() {
        String ids = IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

        assertThatThrownBy(() -> groupService.getGroupsByIds(ids))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getGroupsByIds_중복을_제거하면_50개_이하면_통과한다() {
        List<String> tokens = new ArrayList<>();
        IntStream.rangeClosed(1, 50).forEach(i -> tokens.add(String.valueOf(i)));
        tokens.add("1");
        tokens.add("2");
        String ids = String.join(",", tokens);
        List<Long> distinctIds = IntStream.rangeClosed(1, 50).mapToObj(i -> (long) i).toList();

        given(clubGroupRepository.findAllById(distinctIds)).willReturn(List.of());
        given(groupTermRepository.findAllByGroupIdIn(List.of())).willReturn(List.of());

        GroupSummaryListResponse response = groupService.getGroupsByIds(ids);

        assertThat(response.getGroups()).isEmpty();
    }

    private ClubGroup group(Long id, String name) {
        ClubGroup group = ClubGroup.create(name);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private GroupTerm term(Long id, ClubGroup group, String name) {
        GroupTerm term = GroupTerm.create(group, name);
        ReflectionTestUtils.setField(term, "id", id);
        return term;
    }
}
