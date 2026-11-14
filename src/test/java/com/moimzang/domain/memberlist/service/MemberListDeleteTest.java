package com.moimzang.domain.memberlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
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

// 명단방 삭제는 FK가 전부 RESTRICT인 출석·정산·참가자·부원 테이블을 순서대로 지워야 해서 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class, MemberListService.class})
class MemberListDeleteTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberListService memberListService;

    private GroupTerm groupTerm;
    private Schedule schedule;
    private ScheduleRound round;

    @BeforeEach
    void setUp() {
        ClubGroup group = em.persist(ClubGroup.create("테스트 동아리"));
        groupTerm = em.persist(GroupTerm.create(group, "26기"));
        schedule = em.persist(Schedule.create(groupTerm, null, "가을 MT", "GREEN", false,
                LocalDateTime.of(2026, 11, 21, 10, 0), LocalDateTime.of(2026, 11, 22, 12, 0), "가평", null, true));
        round = em.persist(ScheduleRound.create(schedule, 1, "1차"));
        em.flush();
    }

    @Test
    void 부원과_그_부원의_참가_출석_정산_기록까지_지우고_다른_명단방과_현장_참가자는_남긴다() {
        MemberList target = em.persist(memberList("26기 부원", "token-target"));
        MemberList other = em.persist(memberList("MT 참가자", "token-other"));
        Member targetMember = em.persist(member(target, "강서준"));
        Member otherMember = em.persist(member(other, "김하늘"));

        ScheduleAttendee targetAttendee = em.persist(attendee(targetMember));
        ScheduleAttendee otherAttendee = em.persist(attendee(otherMember));
        ScheduleAttendee onSite = em.persist(ScheduleAttendee.createOnSite(schedule, "박지민", "경영학과", "1234"));
        em.persist(RoundAttendee.create(round, targetAttendee));
        em.persist(RoundAttendee.create(round, otherAttendee));
        em.persist(RoundAttendee.create(round, onSite));
        em.persist(settlementTarget(targetAttendee));
        em.persist(settlementTarget(otherAttendee));
        em.flush();

        memberListService.deleteMemberList(target.getId());
        em.flush();
        em.clear();

        assertThat(count("member_list", "id", target.getId())).isZero();
        assertThat(count("member", "member_list_id", target.getId())).isZero();
        assertThat(count("schedule_attendee", "id", targetAttendee.getId())).isZero();
        assertThat(count("round_attendee", "schedule_attendee_id", targetAttendee.getId())).isZero();
        assertThat(count("settlement_target", "schedule_attendee_id", targetAttendee.getId())).isZero();

        assertThat(count("member_list", "id", other.getId())).isEqualTo(1);
        assertThat(count("member", "member_list_id", other.getId())).isEqualTo(1);
        assertThat(count("schedule_attendee", "id", otherAttendee.getId())).isEqualTo(1);
        assertThat(count("round_attendee", "schedule_attendee_id", otherAttendee.getId())).isEqualTo(1);
        assertThat(count("settlement_target", "schedule_attendee_id", otherAttendee.getId())).isEqualTo(1);
        assertThat(count("schedule_attendee", "id", onSite.getId())).isEqualTo(1);
        assertThat(count("round_attendee", "schedule_attendee_id", onSite.getId())).isEqualTo(1);
    }

    @Test
    void 부원이_없는_명단방도_삭제된다() {
        MemberList empty = em.persist(memberList("빈 명단", "token-empty"));
        em.flush();

        memberListService.deleteMemberList(empty.getId());
        em.flush();
        em.clear();

        assertThat(count("member_list", "id", empty.getId())).isZero();
    }

    private long count(String table, String column, Long value) {
        Number result = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")
                .setParameter(1, value)
                .getSingleResult();
        return result.longValue();
    }

    private MemberList memberList(String name, String token) {
        return MemberList.create(groupTerm, name, LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                true, true, true, true, true, token);
    }

    private Member member(MemberList list, String name) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "memberList", list);
        ReflectionTestUtils.setField(member, "name", name);
        ReflectionTestUtils.setField(member, "department", "컴퓨터공학과");
        ReflectionTestUtils.setField(member, "phone", "010-1234-4827");
        ReflectionTestUtils.setField(member, "privacyConsent", true);
        return member;
    }

    private ScheduleAttendee attendee(Member member) {
        ScheduleAttendee attendee = ScheduleAttendee.createOnSite(schedule, member.getName(), "컴퓨터공학과", "4827");
        ReflectionTestUtils.setField(attendee, "member", member);
        return attendee;
    }

    private SettlementTarget settlementTarget(ScheduleAttendee attendee) {
        SettlementTarget settlementTarget = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(settlementTarget, "scheduleRound", round);
        ReflectionTestUtils.setField(settlementTarget, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(settlementTarget, "billedAmount", 4000);
        ReflectionTestUtils.setField(settlementTarget, "paymentStatus", PaymentStatus.UNPAID);
        return settlementTarget;
    }
}
