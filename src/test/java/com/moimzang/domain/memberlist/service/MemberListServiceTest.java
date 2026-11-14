package com.moimzang.domain.memberlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.memberlist.dto.request.MemberListCreateRequest;
import com.moimzang.domain.memberlist.dto.request.MemberListUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberListCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListDetailResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListListResponse;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
import com.moimzang.domain.memberlist.exception.MemberListErrorCode;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberListServiceTest {

    @Mock
    private MemberListRepository memberListRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private ScheduleAttendeeRepository scheduleAttendeeRepository;

    @Mock
    private RoundAttendeeRepository roundAttendeeRepository;

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    private MemberListService memberListService;

    @BeforeEach
    void setUp() {
        memberListService = new MemberListService(memberListRepository, memberRepository, groupTermRepository,
                scheduleAttendeeRepository, roundAttendeeRepository, settlementTargetRepository);
        ReflectionTestUtils.setField(memberListService, "joinBaseUrl", "moimjang.app/join/");
    }

    @Test
    void createMemberList_토글을_안보내면_전부_수집하고_마감_일시는_받은_그대로_저장한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(memberListRepository.existsByJoinToken(anyString())).willReturn(false);
        given(memberListRepository.save(any(MemberList.class))).willAnswer(invocation -> {
            MemberList saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        MemberListCreateResponse response = memberListService.createMemberList(1L, MemberListCreateRequest.builder()
                .name(" 26기 신입 부원 ")
                .registerDeadline(LocalDateTime.of(2026, 11, 30, 23, 59))
                .collectPhone(false)
                .build());

        ArgumentCaptor<MemberList> captor = ArgumentCaptor.forClass(MemberList.class);
        verify(memberListRepository).save(captor.capture());
        MemberList saved = captor.getValue();
        assertThat(saved.getGroupTerm()).isSameAs(groupTerm);
        assertThat(saved.getName()).isEqualTo("26기 신입 부원");
        assertThat(saved.getRegisterDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 59));
        assertThat(saved.getCollectSchool()).isTrue();
        assertThat(saved.getCollectStudentId()).isTrue();
        assertThat(saved.getCollectPart()).isTrue();
        assertThat(saved.getCollectGrade()).isTrue();
        assertThat(saved.getCollectPhone()).isFalse();
        assertThat(saved.getCollectDepartment()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(MemberListStatus.RECRUITING);
        assertThat(saved.getIsLinkClosed()).isFalse();
        assertThat(response.getMemberListId()).isEqualTo(3L);
        assertThat(response.getJoinToken()).isEqualTo(saved.getJoinToken()).matches("[A-Za-z0-9_-]{16}");
        assertThat(response.getJoinUrl()).isEqualTo("moimjang.app/join/" + saved.getJoinToken());
    }

    @Test
    void createMemberList_토큰이_겹치면_다시_발급한다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));
        given(memberListRepository.existsByJoinToken(anyString())).willReturn(true, false);
        given(memberListRepository.save(any(MemberList.class))).willAnswer(invocation -> invocation.getArgument(0));

        memberListService.createMemberList(1L, createRequest());

        verify(memberListRepository, times(2)).existsByJoinToken(anyString());
    }

    @Test
    void createMemberList_기수가_없으면_MEMBER_LIST_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberListService.createMemberList(9L, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberListErrorCode.MEMBER_LIST_404_GROUP_TERM_NOT_FOUND);
        verify(memberListRepository, never()).save(any());
    }

    @Test
    void listMemberLists_인원수와_상태를_함께_내려주고_부원이_없으면_0명이다() {
        MemberList recruiting = memberList(1L, "26기 부원", today().plusDays(3), false);
        MemberList closed = memberList(2L, "MT 참가자", today().plusDays(3), true);
        MemberList expired = memberList(3L, "OT 참가자", today().minusDays(1), false);
        given(groupTermRepository.existsById(1L)).willReturn(true);
        given(memberListRepository.findByGroupTermIdOrderByIdDesc(1L)).willReturn(List.of(recruiting, closed, expired));
        given(memberRepository.countByMemberListIds(List.of(1L, 2L, 3L)))
                .willReturn(List.of(countRow(1L, 24L), countRow(3L, 5L)));

        MemberListListResponse response = memberListService.listMemberLists(1L);

        assertThat(response.getMemberLists()).hasSize(3);
        assertThat(response.getMemberLists().get(0).getMemberCount()).isEqualTo(24L);
        assertThat(response.getMemberLists().get(0).getStatus()).isEqualTo(MemberListStatus.RECRUITING);
        assertThat(response.getMemberLists().get(0).getRegisterDeadline()).isEqualTo(today().plusDays(3).atTime(23, 59, 59));
        assertThat(response.getMemberLists().get(1).getMemberCount()).isZero();
        assertThat(response.getMemberLists().get(1).getStatus()).isEqualTo(MemberListStatus.COMPLETED);
        assertThat(response.getMemberLists().get(2).getStatus()).isEqualTo(MemberListStatus.COMPLETED);
    }

    @Test
    void listMemberLists_명단방이_없으면_인원수를_조회하지_않는다() {
        given(groupTermRepository.existsById(1L)).willReturn(true);
        given(memberListRepository.findByGroupTermIdOrderByIdDesc(1L)).willReturn(List.of());

        assertThat(memberListService.listMemberLists(1L).getMemberLists()).isEmpty();
        verify(memberRepository, never()).countByMemberListIds(any());
    }

    @Test
    void listMemberLists_기수가_없으면_MEMBER_LIST_404_GROUP_TERM_NOT_FOUND() {
        given(groupTermRepository.existsById(9L)).willReturn(false);

        assertThatThrownBy(() -> memberListService.listMemberLists(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberListErrorCode.MEMBER_LIST_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void getMemberList_링크와_수집_항목을_내려준다() {
        MemberList memberList = memberList(1L, "26기 부원", today().plusDays(3), false);
        given(memberListRepository.findById(1L)).willReturn(Optional.of(memberList));
        given(memberRepository.countByMemberListId(1L)).willReturn(24L);

        MemberListDetailResponse response = memberListService.getMemberList(1L);

        assertThat(response.getName()).isEqualTo("26기 부원");
        assertThat(response.getMemberCount()).isEqualTo(24L);
        assertThat(response.getStatus()).isEqualTo(MemberListStatus.RECRUITING);
        assertThat(response.getJoinUrl()).isEqualTo("moimjang.app/join/" + memberList.getJoinToken());
        assertThat(response.getIsLinkClosed()).isFalse();
        assertThat(response.getFields().getSchool()).isTrue();
        assertThat(response.getFields().getPhone()).isFalse();
    }

    @Test
    void getMemberList_명단방이_없으면_MEMBER_LIST_404_NOT_FOUND() {
        given(memberListRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberListService.getMemberList(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
    }

    @Test
    void updateMemberList_보낸_항목만_바꾸고_토큰은_그대로다() {
        MemberList memberList = memberList(1L, "26기 부원", today().plusDays(3), false);
        String token = memberList.getJoinToken();
        given(memberListRepository.findById(1L)).willReturn(Optional.of(memberList));

        memberListService.updateMemberList(1L, MemberListUpdateRequest.builder()
                .name(" 26기 정규 부원 ")
                .registerDeadline(LocalDateTime.of(2026, 12, 5, 23, 59))
                .collectPhone(true)
                .build());

        assertThat(memberList.getName()).isEqualTo("26기 정규 부원");
        assertThat(memberList.getRegisterDeadline()).isEqualTo(LocalDateTime.of(2026, 12, 5, 23, 59));
        assertThat(memberList.getCollectPhone()).isTrue();
        assertThat(memberList.getCollectSchool()).isTrue();
        assertThat(memberList.getJoinToken()).isEqualTo(token);
    }

    @Test
    void updateMemberList_명단방이_없으면_MEMBER_LIST_404_NOT_FOUND() {
        given(memberListRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberListService.updateMemberList(9L, MemberListUpdateRequest.builder().build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
    }

    @Test
    void deleteMemberList_출석_정산_참가자_부원_명단방_순서로_지운다() {
        given(memberListRepository.findById(1L))
                .willReturn(Optional.of(memberList(1L, "26기 부원", today().plusDays(3), false)));

        memberListService.deleteMemberList(1L);

        InOrder order = inOrder(roundAttendeeRepository, settlementTargetRepository, scheduleAttendeeRepository,
                memberRepository, memberListRepository);
        order.verify(roundAttendeeRepository).deleteAllByMemberListId(1L);
        order.verify(settlementTargetRepository).deleteAllByMemberListId(1L);
        order.verify(scheduleAttendeeRepository).deleteAllByMemberListId(1L);
        order.verify(memberRepository).deleteAllByMemberListId(1L);
        order.verify(memberListRepository).deleteById(1L);
    }

    @Test
    void deleteMemberList_명단방이_없으면_아무것도_지우지_않는다() {
        given(memberListRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberListService.deleteMemberList(9L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
        verify(memberRepository, never()).deleteAllByMemberListId(any());
        verify(memberListRepository, never()).deleteById(any());
    }

    private MemberListCreateRequest createRequest() {
        return MemberListCreateRequest.builder()
                .name("26기 부원")
                .registerDeadline(LocalDateTime.of(2026, 11, 30, 23, 59))
                .build();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private MemberList memberList(Long id, String name, LocalDate deadline, boolean linkClosed) {
        MemberList memberList = MemberList.create(mock(GroupTerm.class), name, deadline.atTime(23, 59, 59),
                true, true, true, true, false, "token-" + id);
        ReflectionTestUtils.setField(memberList, "id", id);
        ReflectionTestUtils.setField(memberList, "isLinkClosed", linkClosed);
        return memberList;
    }

    private MemberRepository.MemberCountRow countRow(Long memberListId, Long count) {
        return new MemberRepository.MemberCountRow() {
            @Override
            public Long getMemberListId() {
                return memberListId;
            }

            @Override
            public Long getMemberCount() {
                return count;
            }
        };
    }
}
