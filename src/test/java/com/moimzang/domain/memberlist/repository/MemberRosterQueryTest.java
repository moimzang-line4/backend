package com.moimzang.domain.memberlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 명단 조회 탭은 검색 조건이 여러 컬럼에 걸치고 같은 초에 등록한 부원을 id로 가르는 cursor라 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class MemberRosterQueryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    private MemberList memberList;
    private MemberList otherList;

    @BeforeEach
    void setUp() {
        ClubGroup group = em.persist(ClubGroup.create("음샘"));
        GroupTerm groupTerm = em.persist(GroupTerm.create(group, "26기"));
        memberList = em.persist(memberList(groupTerm, "2026년 2학기 음샘", "token-a"));
        otherList = em.persist(memberList(groupTerm, "2026년 1학기 음샘", "token-b"));
        em.flush();
    }

    @Test
    void 등록한_순서대로_cursor로_끝까지_넘기면_빠짐없이_한번씩_조회된다() {
        for (int i = 1; i <= 5; i++) {
            em.persist(member(memberList, "부원" + i, "전자전기공학부", "010-0000-000" + i));
        }
        em.persist(member(otherList, "다른 명단 부원", "경영학과", "010-1111-1111"));
        em.flush();
        em.clear();

        List<Member> first = memberRepository.searchInMemberList(memberList.getId(), null, null, null, 2);
        Member lastOfFirst = first.get(first.size() - 1);
        List<Member> second = memberRepository.searchInMemberList(memberList.getId(), null,
                lastOfFirst.getCreatedAt(), lastOfFirst.getId(), 2);
        Member lastOfSecond = second.get(second.size() - 1);
        List<Member> third = memberRepository.searchInMemberList(memberList.getId(), null,
                lastOfSecond.getCreatedAt(), lastOfSecond.getId(), 2);

        assertThat(first).extracting(Member::getName).containsExactly("부원1", "부원2");
        assertThat(second).extracting(Member::getName).containsExactly("부원3", "부원4");
        assertThat(third).extracting(Member::getName).containsExactly("부원5");
    }

    @Test
    void 이름_학과_전화번호로_검색하고_전체_인원도_같은_조건으로_센다() {
        em.persist(member(memberList, "한지우", "전자전기공학부", "010-6789-0123"));
        em.persist(member(memberList, "강민준", "광고홍보학과", "010-1234-1140"));
        em.persist(member(memberList, "김서경", "전자전기공학부", null));
        em.persist(member(otherList, "한지우", "전자전기공학부", "010-6789-0123"));
        em.flush();
        em.clear();

        assertThat(memberRepository.searchInMemberList(memberList.getId(), "지우", null, null, 10))
                .extracting(Member::getName).containsExactly("한지우");
        assertThat(memberRepository.searchInMemberList(memberList.getId(), "전자", null, null, 10))
                .extracting(Member::getName).containsExactly("한지우", "김서경");
        assertThat(memberRepository.searchInMemberList(memberList.getId(), "1140", null, null, 10))
                .extracting(Member::getName).containsExactly("강민준");
        assertThat(memberRepository.countInMemberList(memberList.getId(), null)).isEqualTo(3);
        assertThat(memberRepository.countInMemberList(memberList.getId(), "전자")).isEqualTo(2);
    }

    private MemberList memberList(GroupTerm groupTerm, String name, String token) {
        return MemberList.create(groupTerm, name, LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                true, true, true, true, true, token);
    }

    private Member member(MemberList list, String name, String department, String phone) {
        return Member.create(list, name, "동국대학교", department, phone, null, null, null, true);
    }
}
