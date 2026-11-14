package com.moimzang.domain.memberlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.mapper.MemberListMapperImpl;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
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

// 부원을 가리키는 일정 참가자 FK가 RESTRICT라, 연결을 먼저 끊어야 삭제되는지 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class, MemberService.class, MemberListMapperImpl.class})
class MemberDeleteTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberService memberService;

    @Test
    void 부원을_지워도_일정_참가자와_출석_기록은_남고_부원_연결만_끊긴다() {
        ClubGroup group = em.persist(ClubGroup.create("음샘"));
        GroupTerm groupTerm = em.persist(GroupTerm.create(group, "26기"));
        MemberList memberList = em.persist(MemberList.create(groupTerm, "2026년 2학기 음샘",
                LocalDateTime.of(2026, 11, 30, 23, 59, 59), true, true, true, true, true, "token-a"));
        Member member = em.persist(Member.create(memberList, "강서준", null, "컴퓨터공학과", "010-1234-4827",
                null, null, null, true));
        Schedule schedule = em.persist(Schedule.create(groupTerm, null, "가을 MT", "GREEN", false,
                LocalDateTime.of(2026, 11, 21, 10, 0), LocalDateTime.of(2026, 11, 22, 12, 0), "가평", null, true));
        ScheduleRound round = em.persist(ScheduleRound.create(schedule, 1, "1차"));
        ScheduleAttendee attendee = ScheduleAttendee.createOnSite(schedule, "강서준", "컴퓨터공학과", "4827");
        ReflectionTestUtils.setField(attendee, "member", member);
        em.persist(attendee);
        em.persist(RoundAttendee.create(round, attendee));
        em.flush();

        memberService.deleteMember(member.getId());
        em.flush();
        em.clear();

        assertThat(count("SELECT COUNT(*) FROM member WHERE id = ?", member.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM schedule_attendee WHERE id = ?", attendee.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM schedule_attendee WHERE id = ? AND member_id IS NULL",
                attendee.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM round_attendee WHERE schedule_attendee_id = ?", attendee.getId()))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM member_list WHERE id = ?", memberList.getId())).isEqualTo(1);
    }

    private long count(String sql, Long value) {
        Number result = (Number) em.getEntityManager()
                .createNativeQuery(sql)
                .setParameter(1, value)
                .getSingleResult();
        return result.longValue();
    }
}
