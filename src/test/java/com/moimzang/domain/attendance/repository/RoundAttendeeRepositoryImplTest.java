package com.moimzang.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.enums.AttendeeSource;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import java.util.List;
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

// QueryDSL 동적 조건(status/keyword)·fetch join·cursor tiebreaker는 Mockito 단위 테스트로는
// 검증할 수 없는 영역이라 실제 MySQL로 검증한다(MeetingRepositoryImplTest와 같은 패턴)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class RoundAttendeeRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RoundAttendeeRepository roundAttendeeRepository;

    private Schedule schedule;
    private ScheduleRound round;

    @BeforeEach
    void setUp() {
        ClubGroup clubGroup = persistClubGroup("테스트 동아리");
        GroupTerm groupTerm = persistGroupTerm(clubGroup, "26기");
        schedule = persistSchedule(groupTerm, "정기 모임");
        round = em.persistAndFlush(ScheduleRound.create(schedule, 1, "1차"));
    }

    @Test
    void search_다른_차수의_참가자는_제외하고_이_차수의_참가자만_반환한다() {
        ScheduleAttendee attendee = persistScheduleAttendee("김모임", "컴퓨터공학과");
        RoundAttendee target = em.persistAndFlush(RoundAttendee.create(round, attendee));

        ScheduleRound otherRound = em.persistAndFlush(ScheduleRound.create(schedule, 2, "2차"));
        ScheduleAttendee otherAttendee = persistScheduleAttendee("박모임", "경영학과");
        em.persistAndFlush(RoundAttendee.create(otherRound, otherAttendee));

        List<RoundAttendee> result = roundAttendeeRepository.search(round.getId(), null, null, null, null, 10);

        assertThat(result).extracting(RoundAttendee::getId).containsExactly(target.getId());
    }

    @Test
    void search_isCheckedIn_필터로_출석한_사람만_반환한다() {
        RoundAttendee checked = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("김모임", "컴퓨터공학과")));
        ReflectionTestUtils.setField(checked, "isCheckedIn", true);
        ReflectionTestUtils.setField(checked, "checkedInAt", LocalDateTime.now());
        em.persistAndFlush(checked);

        RoundAttendee unchecked = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("박모임", "경영학과")));

        List<RoundAttendee> result = roundAttendeeRepository.search(round.getId(), true, null, null, null, 10);

        assertThat(result).extracting(RoundAttendee::getId).containsExactly(checked.getId());
        assertThat(result).extracting(RoundAttendee::getId).doesNotContain(unchecked.getId());
    }

    @Test
    void search_keyword로_이름을_대소문자_구분없이_포함검색한다() {
        RoundAttendee matched = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("Alice", "컴퓨터공학과")));
        em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("Bob", "경영학과")));

        List<RoundAttendee> result = roundAttendeeRepository.search(round.getId(), null, "ali", null, null, 10);

        assertThat(result).extracting(RoundAttendee::getId).containsExactly(matched.getId());
    }

    @Test
    void search_정렬은_createdAt_오름차순이고_cursor는_그_지점_이후부터_반환한다() {
        RoundAttendee first = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("1번째", null)));
        RoundAttendee second = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("2번째", null)));
        RoundAttendee third = em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("3번째", null)));

        List<RoundAttendee> all = roundAttendeeRepository.search(round.getId(), null, null, null, null, 10);
        assertThat(all).extracting(RoundAttendee::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());

        // 인메모리 createdAt은 나노초를 갖지만 DATETIME 컬럼은 초 단위로 반올림해 저장한다
        // 실제 API처럼 DB에 저장된 값을 cursor로 쓰도록 영속성 컨텍스트를 비우고 다시 읽는다
        em.clear();
        LocalDateTime cursorCreatedAt = em.find(RoundAttendee.class, first.getId()).getCreatedAt();

        // cursor는 (createdAt, id) 복합 비교다 — createdAt이 같은 초에 묶여도 id로 다음 페이지가 갈린다
        List<RoundAttendee> next = roundAttendeeRepository.search(
                round.getId(), null, null, cursorCreatedAt, first.getId(), 10);

        assertThat(next).extracting(RoundAttendee::getId)
                .containsExactly(second.getId(), third.getId());
    }

    @Test
    void search_scheduleAttendee를_fetch_join해서_영속성_컨텍스트_밖에서도_이름을_읽을_수_있다() {
        em.persistAndFlush(RoundAttendee.create(round, persistScheduleAttendee("김모임", "컴퓨터공학과")));
        em.clear();

        List<RoundAttendee> result = roundAttendeeRepository.search(round.getId(), null, null, null, null, 10);

        assertThatCode(() -> result.get(0).getScheduleAttendee().getName()).doesNotThrowAnyException();
        assertThat(result.get(0).getScheduleAttendee().getName()).isEqualTo("김모임");
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

    private Schedule persistSchedule(GroupTerm groupTerm, String title) {
        Schedule newSchedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(newSchedule, "groupTerm", groupTerm);
        ReflectionTestUtils.setField(newSchedule, "title", title);
        ReflectionTestUtils.setField(newSchedule, "color", "BLUE");
        ReflectionTestUtils.setField(newSchedule, "isAllDay", false);
        ReflectionTestUtils.setField(newSchedule, "startAt", LocalDateTime.of(2026, 11, 13, 18, 0));
        ReflectionTestUtils.setField(newSchedule, "endAt", LocalDateTime.of(2026, 11, 13, 20, 0));
        ReflectionTestUtils.setField(newSchedule, "hasOperationSpace", true);
        return em.persistAndFlush(newSchedule);
    }

    private ScheduleAttendee persistScheduleAttendee(String name, String affiliation) {
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "schedule", schedule);
        ReflectionTestUtils.setField(attendee, "name", name);
        ReflectionTestUtils.setField(attendee, "affiliation", affiliation);
        ReflectionTestUtils.setField(attendee, "phoneLast4", "1234");
        ReflectionTestUtils.setField(attendee, "source", AttendeeSource.ON_SITE);
        return em.persistAndFlush(attendee);
    }
}