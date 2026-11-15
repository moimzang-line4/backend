package com.moimzang.domain.memberlist.service;

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

import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.dto.request.MemberAddRequest;
import com.moimzang.domain.memberlist.dto.request.MemberRegisterRequest;
import com.moimzang.domain.memberlist.dto.request.MemberUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberJoinFormResponse;
import com.moimzang.domain.memberlist.dto.response.MemberPageResponse;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.exception.MemberListErrorCode;
import com.moimzang.domain.memberlist.mapper.MemberListMapper;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import com.moimzang.global.cursor.CursorCodec;
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
class MemberServiceTest {

    @Mock
    private MemberListRepository memberListRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ScheduleAttendeeRepository scheduleAttendeeRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 실제 생성 코드로 검증한다
    private final MemberListMapper memberListMapper = Mappers.getMapper(MemberListMapper.class);

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberListRepository, memberRepository, scheduleAttendeeRepository,
                memberListMapper);
    }

    @Test
    void getJoinForm_그룹_이름과_켜진_항목을_내려준다() {
        MemberList memberList = openList(true, false);
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));

        MemberJoinFormResponse response = memberService.getJoinForm("token");

        assertThat(response.getGroupName()).isEqualTo("음샘");
        assertThat(response.getMemberListName()).isEqualTo("2026년 2학기 음샘");
        assertThat(response.getRegisterDeadline()).isEqualTo(today().plusDays(3).atTime(23, 59, 59));
        assertThat(response.getFields().getSchool()).isTrue();
        assertThat(response.getFields().getPhone()).isFalse();
    }

    @Test
    void getJoinForm_없는_링크면_MEMBER_404_JOIN_LINK_NOT_FOUND() {
        given(memberListRepository.findByJoinToken("nope")).willReturn(Optional.empty());

        assertError(() -> memberService.getJoinForm("nope"), MemberListErrorCode.MEMBER_404_JOIN_LINK_NOT_FOUND);
    }

    @Test
    void getJoinForm_링크를_닫았으면_MEMBER_410_JOIN_CLOSED() {
        MemberList memberList = openList(true, true);
        ReflectionTestUtils.setField(memberList, "isLinkClosed", true);
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));

        assertError(() -> memberService.getJoinForm("token"), MemberListErrorCode.MEMBER_410_JOIN_CLOSED);
    }

    @Test
    void getJoinForm_마감일이_지났으면_MEMBER_410_JOIN_CLOSED() {
        MemberList memberList = openList(true, true);
        ReflectionTestUtils.setField(memberList, "registerDeadline", today().minusDays(1).atTime(23, 59, 59));
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));

        assertError(() -> memberService.getJoinForm("token"), MemberListErrorCode.MEMBER_410_JOIN_CLOSED);
    }

    @Test
    void registerMember_켜진_항목만_저장하고_꺼진_항목은_보내도_버린다() {
        MemberList memberList = openList(true, false);
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        MemberCreateResponse response = memberService.registerMember("token", registerRequest()
                .phone("010-0000-0000")
                .build());

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getMemberList()).isSameAs(memberList);
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getSchool()).isEqualTo("동국대학교");
        assertThat(saved.getDepartment()).isEqualTo("광고홍보학과");
        assertThat(saved.getStudentId()).isEqualTo("2026123456");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getPrivacyConsent()).isTrue();
        assertThat(response.getMemberId()).isEqualTo(101L);
    }

    @Test
    void registerMember_이름과_전화번호가_같은_부원이_있으면_MEMBER_409_ALREADY_REGISTERED() {
        MemberList memberList = openList(true, true);
        ReflectionTestUtils.setField(memberList, "id", 2L);
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));
        given(memberRepository.existsByMemberListIdAndNameAndPhone(2L, "홍길동", "010-0000-0000")).willReturn(true);

        assertError(() -> memberService.registerMember("token", registerRequest().phone(" 010-0000-0000 ").build()),
                MemberListErrorCode.MEMBER_409_ALREADY_REGISTERED);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void registerMember_전화번호를_받지_않는_명단방은_중복_검사를_하지_않는다() {
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(openList(true, false)));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        memberService.registerMember("token", registerRequest().phone("010-0000-0000").build());

        verify(memberRepository, never()).existsByMemberListIdAndNameAndPhone(any(), any(), any());
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void addMember_운영진_추가는_중복_검사를_하지_않는다() {
        given(memberListRepository.findById(2L)).willReturn(Optional.of(openList(true, true)));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        memberService.addMember(2L, MemberAddRequest.builder().name("홍길동").department("광고홍보학과")
                .phone("010-0000-0000").build());

        verify(memberRepository, never()).existsByMemberListIdAndNameAndPhone(any(), any(), any());
    }

    @Test
    void registerMember_켜진_항목이_비어_있으면_MEMBER_400_REQUIRED_FIELD_MISSING() {
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(openList(true, false)));

        assertError(() -> memberService.registerMember("token", registerRequest().school("  ").build()),
                MemberListErrorCode.MEMBER_400_REQUIRED_FIELD_MISSING);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void registerMember_개인정보에_동의하지_않으면_MEMBER_400_PRIVACY_CONSENT_REQUIRED() {
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(openList(true, false)));

        assertError(() -> memberService.registerMember("token", registerRequest().privacyConsent(false).build()),
                MemberListErrorCode.MEMBER_400_PRIVACY_CONSENT_REQUIRED);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void registerMember_마감된_명단이면_저장하지_않는다() {
        MemberList memberList = openList(true, false);
        ReflectionTestUtils.setField(memberList, "isLinkClosed", true);
        given(memberListRepository.findByJoinToken("token")).willReturn(Optional.of(memberList));

        assertError(() -> memberService.registerMember("token", registerRequest().build()),
                MemberListErrorCode.MEMBER_410_JOIN_CLOSED);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void listMembers_다음_페이지가_있으면_마지막_부원으로_cursor를_만든다() {
        Member first = member(101L, LocalDateTime.of(2026, 11, 1, 10, 0));
        Member second = member(102L, LocalDateTime.of(2026, 11, 1, 10, 5));
        Member extra = member(103L, LocalDateTime.of(2026, 11, 1, 10, 9));
        given(memberListRepository.existsById(2L)).willReturn(true);
        given(memberRepository.searchInMemberList(2L, "전자", null, null, 3)).willReturn(List.of(first, second, extra));
        given(memberRepository.countInMemberList(2L, "전자")).willReturn(8L);

        MemberPageResponse response = memberService.listMembers(2L, "전자", null, 2);

        assertThat(response.getTotalCount()).isEqualTo(8L);
        assertThat(response.getMembers()).extracting("memberId").containsExactly(101L, 102L);
        assertThat(response.getMembers().get(0).getStudentId()).isNull();
        assertThat(response.getNextCursor()).isEqualTo(CursorCodec.encode("2026-11-01T10:05", 102L));
    }

    @Test
    void listMembers_cursor를_풀어서_다음_페이지를_조회하고_마지막_페이지면_cursor는_null이다() {
        given(memberListRepository.existsById(2L)).willReturn(true);
        given(memberRepository.searchInMemberList(eq(2L), isNull(), eq(LocalDateTime.of(2026, 11, 1, 10, 5)),
                eq(102L), eq(21))).willReturn(List.of(member(103L, LocalDateTime.of(2026, 11, 1, 10, 9))));
        given(memberRepository.countInMemberList(2L, null)).willReturn(3L);

        MemberPageResponse response = memberService.listMembers(2L, null,
                CursorCodec.encode("2026-11-01T10:05", 102L), null);

        assertThat(response.getMembers()).extracting("memberId").containsExactly(103L);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listMembers_cursor가_깨졌으면_MEMBER_400_INVALID_CURSOR() {
        given(memberListRepository.existsById(2L)).willReturn(true);

        assertError(() -> memberService.listMembers(2L, null, "broken", null),
                MemberListErrorCode.MEMBER_400_INVALID_CURSOR);
    }

    @Test
    void listMembers_명단방이_없으면_MEMBER_LIST_404_NOT_FOUND() {
        given(memberListRepository.existsById(9L)).willReturn(false);

        assertError(() -> memberService.listMembers(9L, null, null, null),
                MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
    }

    @Test
    void addMember_학교_전화번호는_선택이고_폼_동의를_거치지_않아_동의는_false로_남긴다() {
        MemberList memberList = openList(true, true);
        given(memberListRepository.findById(2L)).willReturn(Optional.of(memberList));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        memberService.addMember(2L, MemberAddRequest.builder()
                .name(" 한지우 ")
                .department("전자전기공학부")
                .school(" ")
                .build());

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("한지우");
        assertThat(captor.getValue().getSchool()).isNull();
        assertThat(captor.getValue().getPhone()).isNull();
        assertThat(captor.getValue().getPrivacyConsent()).isFalse();
    }

    @Test
    void addMember_명단방이_없으면_MEMBER_LIST_404_NOT_FOUND() {
        given(memberListRepository.findById(9L)).willReturn(Optional.empty());

        assertError(() -> memberService.addMember(9L, MemberAddRequest.builder().name("한지우")
                .department("전자전기공학부").build()), MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
    }

    @Test
    void updateMember_보낸_필드만_바꾸고_빈_전화번호는_비운다() {
        Member member = Member.create(openList(true, true), "한지우", "동국대학교", "전자전기공학부", "010-6789-0123",
                "2026123456", null, null, true);
        given(memberRepository.findById(101L)).willReturn(Optional.of(member));

        memberService.updateMember(101L, MemberUpdateRequest.builder()
                .department(" 컴퓨터공학과 ")
                .phone("")
                .build());

        assertThat(member.getName()).isEqualTo("한지우");
        assertThat(member.getSchool()).isEqualTo("동국대학교");
        assertThat(member.getDepartment()).isEqualTo("컴퓨터공학과");
        assertThat(member.getPhone()).isNull();
        assertThat(member.getStudentId()).isEqualTo("2026123456");
    }

    @Test
    void updateMember_부원이_없으면_MEMBER_404_NOT_FOUND() {
        given(memberRepository.findById(9L)).willReturn(Optional.empty());

        assertError(() -> memberService.updateMember(9L, MemberUpdateRequest.builder().build()),
                MemberListErrorCode.MEMBER_404_NOT_FOUND);
    }

    @Test
    void deleteMember_참가자_연결을_끊고_부원을_지운다() {
        given(memberRepository.findById(101L)).willReturn(Optional.of(mock(Member.class)));

        memberService.deleteMember(101L);

        InOrder order = inOrder(scheduleAttendeeRepository, memberRepository);
        order.verify(scheduleAttendeeRepository).clearMemberReference(101L);
        order.verify(memberRepository).deleteById(101L);
    }

    @Test
    void deleteMember_부원이_없으면_아무것도_지우지_않는다() {
        given(memberRepository.findById(9L)).willReturn(Optional.empty());

        assertError(() -> memberService.deleteMember(9L), MemberListErrorCode.MEMBER_404_NOT_FOUND);
        verify(scheduleAttendeeRepository, never()).clearMemberReference(any());
        verify(memberRepository, never()).deleteById(any());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, MemberListErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private MemberRegisterRequest.MemberRegisterRequestBuilder registerRequest() {
        return MemberRegisterRequest.builder()
                .name(" 홍길동 ")
                .school("동국대학교")
                .department("광고홍보학과")
                .studentId("2026123456")
                .part("보컬")
                .grade("1학년")
                .privacyConsent(true);
    }

    // 학교·학번·파트·학년은 켜고, 전화번호만 인자로 정한다
    private MemberList openList(boolean collectSchool, boolean collectPhone) {
        GroupTerm groupTerm = GroupTerm.create(ClubGroup.create("음샘"), "26기");
        return MemberList.create(groupTerm, "2026년 2학기 음샘", today().plusDays(3).atTime(23, 59, 59),
                collectSchool, true, true, true, collectPhone, "token");
    }

    private Member member(Long id, LocalDateTime createdAt) {
        Member member = Member.create(openList(true, true), "부원" + id, null, "전자전기공학부", null,
                null, null, null, true);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        return member;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
