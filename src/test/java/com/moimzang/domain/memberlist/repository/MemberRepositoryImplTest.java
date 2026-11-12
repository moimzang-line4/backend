package com.moimzang.domain.memberlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 부원 조회는 명단방을 거쳐 기수로 올라가는 조인이고 부분 일치 조건이 있어 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class MemberRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    private GroupTerm groupTerm;
    private MemberList memberList;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
        memberList = persistMemberList(groupTerm, "26기 부원 명단");
    }

    @Test
    void searchForAssistant_필터없이_이름순으로_기수의_부원을_반환한다() {
        persistMember(memberList, "홍길동", "기획");
        persistMember(memberList, "강민준", "기획");

        List<Member> result = memberRepository.searchForAssistant(groupTerm.getId(), null, null, 10);

        assertThat(result).extracting(Member::getName).containsExactly("강민준", "홍길동");
    }

    @Test
    void searchForAssistant_이름_부분_일치로_검색한다() {
        Member minjun = persistMember(memberList, "강민준", "기획");
        persistMember(memberList, "홍길동", "기획");

        List<Member> result = memberRepository.searchForAssistant(groupTerm.getId(), "민준", null, 10);

        assertThat(result).extracting(Member::getId).containsExactly(minjun.getId());
    }

    @Test
    void searchForAssistant_파트도_부분_일치로_검색한다() {
        Member planner = persistMember(memberList, "강민준", "기획");
        persistMember(memberList, "홍길동", "홍보");

        // 사용자는 '기획파트'처럼 뒤에 말을 붙여 부르고 저장된 값은 '기획'이다
        List<Member> result = memberRepository.searchForAssistant(groupTerm.getId(), null, "기획", 10);

        assertThat(result).extracting(Member::getId).containsExactly(planner.getId());
    }

    @Test
    void searchForAssistant_다른_기수의_부원은_반환하지_않는다() {
        Member mine = persistMember(memberList, "강민준", "기획");
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        persistMember(persistMemberList(otherTerm, "25기 명단"), "남의 부원", "기획");

        List<Member> result = memberRepository.searchForAssistant(groupTerm.getId(), null, null, 10);

        assertThat(result).extracting(Member::getId).containsExactly(mine.getId());
    }

    @Test
    void searchForAssistant_limit만큼만_반환한다() {
        persistMember(memberList, "강민준", "기획");
        persistMember(memberList, "홍길동", "홍보");
        persistMember(memberList, "김서경", "기획");

        List<Member> result = memberRepository.searchForAssistant(groupTerm.getId(), null, null, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void countForAssistant_같은_조건의_전체_건수를_센다() {
        persistMember(memberList, "강민준", "기획");
        persistMember(memberList, "김서경", "기획");
        persistMember(memberList, "홍길동", "홍보");

        assertThat(memberRepository.countForAssistant(groupTerm.getId(), null, null)).isEqualTo(3);
        assertThat(memberRepository.countForAssistant(groupTerm.getId(), null, "기획")).isEqualTo(2);
        assertThat(memberRepository.countForAssistant(groupTerm.getId(), "민준", null)).isEqualTo(1);
    }

    private ClubGroup persistClubGroup(String name) {
        ClubGroup group = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(group, "name", name);
        return em.persistAndFlush(group);
    }

    private GroupTerm persistGroupTerm(ClubGroup group, String name) {
        GroupTerm term = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(term, "group", group);
        ReflectionTestUtils.setField(term, "name", name);
        return em.persistAndFlush(term);
    }

    private MemberList persistMemberList(GroupTerm term, String name) {
        MemberList list = BeanUtils.instantiateClass(MemberList.class);
        ReflectionTestUtils.setField(list, "groupTerm", term);
        ReflectionTestUtils.setField(list, "name", name);
        ReflectionTestUtils.setField(list, "collectSchool", true);
        ReflectionTestUtils.setField(list, "collectDepartment", true);
        ReflectionTestUtils.setField(list, "collectPhone", true);
        ReflectionTestUtils.setField(list, "collectStudentId", true);
        ReflectionTestUtils.setField(list, "collectPart", true);
        ReflectionTestUtils.setField(list, "collectGrade", true);
        ReflectionTestUtils.setField(list, "status", MemberListStatus.RECRUITING);
        ReflectionTestUtils.setField(list, "joinToken", UUID.randomUUID().toString());
        ReflectionTestUtils.setField(list, "isLinkClosed", false);
        return em.persistAndFlush(list);
    }

    private Member persistMember(MemberList list, String name, String part) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "memberList", list);
        ReflectionTestUtils.setField(member, "name", name);
        ReflectionTestUtils.setField(member, "department", "광고홍보학과");
        ReflectionTestUtils.setField(member, "phone", "010-1234-1140");
        ReflectionTestUtils.setField(member, "part", part);
        ReflectionTestUtils.setField(member, "privacyConsent", true);
        return em.persistAndFlush(member);
    }
}
