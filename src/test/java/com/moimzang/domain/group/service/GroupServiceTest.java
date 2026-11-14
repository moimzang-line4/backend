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
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.exception.GroupErrorCode;
import com.moimzang.domain.group.mapper.GroupMapper;
import com.moimzang.domain.group.repository.ClubGroupRepository;
import com.moimzang.domain.group.repository.GroupTermRepository;
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
